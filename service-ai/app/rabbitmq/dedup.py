"""Redis 분산락 기반 멱등성 게이트 (중복 LLM 요청 방지).

- LLM 호출 전 (area_code, population_time) 키를 SET NX EX 로 선점
  · acquire True  → 이 요청이 선점 → LLM 호출 통과
  · acquire False → 이미 선점(중복 재전달) → LLM 호출 스킵
- 성공 시 마커 유지(TTL 동안 재전달 스킵), 실패 시 release → DLQ 재처리가 재선점
- 값은 선점마다 고유 토큰, release 는 Lua 로 값이 내 토큰일 때만 DEL
  → 처리가 TTL 을 넘겨 다른 컨슈머가 재선점한 락을 지우지 않음
- Redis 장애 시 fail-open: acquire True 반환(기존처럼 LLM 호출). 저장 중복은 DB UNIQUE 가 최종 보증.
- 키는 저장측 DB UNIQUE (area_code, population_time) 와 동일 → 요청·저장 멱등성 정렬

한계: acquire 후 TTL 만료 전 하드 크래시 시 마커가 남아, TTL 내 재전달이 스킵될 수 있는 좁은 창 존재.
완전 차단은 락+완료마커 2키 구조 필요 (별도 이슈). 저장 정확성은 DB UNIQUE 가 보증.
"""

from __future__ import annotations

import logging
import uuid

from redis.asyncio import Redis
from redis.exceptions import RedisError

from app.models.schemas import CongestionEvent

logger = logging.getLogger(__name__)

# 값이 내 토큰일 때만 삭제 (GET + DEL 원자 처리)
_RELEASE_SCRIPT = """
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
end
return 0
"""


class IdempotencyGate:
    """Redis SET NX EX 기반 요청 멱등성 게이트."""

    def __init__(self, redis: Redis, *, key_prefix: str, ttl_seconds: int) -> None:
        self._redis = redis
        self._key_prefix = key_prefix
        self._ttl = ttl_seconds

    def key_for(self, event: CongestionEvent) -> str:
        # DB UNIQUE (area_code, population_time) 와 동일한 멱등성 키
        return f"{self._key_prefix}:{event.area_code}:{event.population_time}"

    async def acquire(self, key: str) -> str | None:
        """SET key token NX EX ttl. 선점 성공 시 토큰 / 이미 존재 시 None.

        Redis 장애 시 fail-open → 토큰 반환 (LLM 호출 통과, 중복 저장은 DB UNIQUE 가 차단).
        """
        token = uuid.uuid4().hex
        try:
            acquired = await self._redis.set(key, token, nx=True, ex=self._ttl)
            return token if acquired else None
        except RedisError as e:
            logger.warning(
                "[Dedup] acquire 실패, fail-open 통과 - key=%s, %s: %r",
                key,
                type(e).__name__,
                e,
            )
            return token

    async def release(self, key: str, token: str) -> None:
        """내 마커만 해제 → DLQ 재처리가 다시 선점 가능. 해제 실패는 무시(마커는 TTL 로 자동 만료)."""
        try:
            await self._redis.eval(_RELEASE_SCRIPT, 1, key, token)
        except RedisError as e:
            logger.warning(
                "[Dedup] release 실패 무시(TTL 만료 대기) - key=%s, %s: %r",
                key,
                type(e).__name__,
                e,
            )
