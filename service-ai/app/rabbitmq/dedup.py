"""Redis 분산락 기반 멱등성 게이트 (중복 LLM 요청 방지).

- LLM 호출 전 (area_code, population_time) 키를 SET NX EX 로 선점
  · acquire True  → 이 요청이 선점 → LLM 호출 통과
  · acquire False → 이미 선점(중복 재전달) → LLM 호출 스킵
- 성공 시 마커 유지(TTL 동안 재전달 스킵), 실패 시 release → DLQ 재처리가 재선점
- Redis 장애 시 fail-open: acquire True 반환(기존처럼 LLM 호출). 저장 중복은 DB UNIQUE 가 최종 보증.
- 키는 저장측 DB UNIQUE (area_code, population_time) 와 동일 → 요청·저장 멱등성 정렬

한계: acquire 후 TTL 만료 전 하드 크래시 시 마커가 남아, TTL 내 재전달이 스킵될 수 있는 좁은 창 존재.
완전 차단은 락+완료마커 2키 구조 필요 (별도 이슈). 저장 정확성은 DB UNIQUE 가 보증.
"""

from __future__ import annotations

import logging

from redis.asyncio import Redis
from redis.exceptions import RedisError

from app.models.schemas import CongestionEvent

logger = logging.getLogger(__name__)


class IdempotencyGate:
    """Redis SET NX EX 기반 요청 멱등성 게이트."""

    def __init__(self, redis: Redis, *, key_prefix: str, ttl_seconds: int) -> None:
        self._redis = redis
        self._key_prefix = key_prefix
        self._ttl = ttl_seconds

    def key_for(self, event: CongestionEvent) -> str:
        # DB UNIQUE (area_code, population_time) 와 동일한 멱등성 키
        return f"{self._key_prefix}:{event.area_code}:{event.population_time}"

    async def acquire(self, key: str) -> bool:
        """SET key NX EX ttl. 선점 성공 True / 이미 존재 False.

        Redis 장애 시 fail-open → True (LLM 호출 통과, 중복 저장은 DB UNIQUE 가 차단).
        """
        try:
            acquired = await self._redis.set(key, "1", nx=True, ex=self._ttl)
            return bool(acquired)
        except RedisError as e:
            logger.warning(
                "[Dedup] acquire 실패, fail-open 통과 - key=%s, %s: %r",
                key,
                type(e).__name__,
                e,
            )
            return True

    async def release(self, key: str) -> None:
        """마커 해제 → DLQ 재처리가 다시 선점 가능. 해제 실패는 무시(마커는 TTL 로 자동 만료)."""
        try:
            await self._redis.delete(key)
        except RedisError as e:
            logger.warning(
                "[Dedup] release 실패 무시(TTL 만료 대기) - key=%s, %s: %r",
                key,
                type(e).__name__,
                e,
            )
