"""토큰 예산 기반 Rate Limiter.

- Cerebras Free Tier (qwen-3-235b-a22b-instruct-2507, 2026-04 확인)
  · TPM 60,000 / TPH 1,000,000 / TPD 1,000,000
  · RPM 30 / RPH 900 / RPD 14,400
- 기존 RPM 기반 구현 한계: 배치 크기 증가 시 TPM 쉽게 초과 (구조적 결함)
- 개선: TPM + TPD 이중 leaky bucket 으로 토큰 단위 강제

API
- await limiter.acquire(estimated_tokens): 두 버킷 여유 확보까지 대기
- limiter.record_actual(actual, estimated): 응답 수신 후 실제 사용량 보정
"""

from __future__ import annotations

import asyncio
import logging
import time

logger = logging.getLogger(__name__)


class _TokenBucket:
    """단순 leaky bucket.

    - capacity: 윈도우 내 최대 토큰 수
    - window_seconds: 완전 충전 소요 시간(초)
    - 누출 속도 = capacity / window_seconds (tokens/sec)
    """

    def __init__(self, capacity: int, window_seconds: float, name: str) -> None:
        self._capacity = float(capacity)
        self._window = float(window_seconds)
        self._available = float(capacity)
        self._last_refill = time.monotonic()
        self._name = name

    @property
    def capacity(self) -> float:
        return self._capacity

    def _refill(self) -> None:
        now = time.monotonic()
        elapsed = now - self._last_refill
        if elapsed <= 0:
            return
        refill_rate = self._capacity / self._window
        self._available = min(self._capacity, self._available + elapsed * refill_rate)
        self._last_refill = now

    def wait_time(self, tokens: float) -> float:
        """tokens 사용 가능 시점까지 대기 시간(초). 여유 있으면 0."""
        self._refill()
        if self._available >= tokens:
            return 0.0
        deficit = tokens - self._available
        refill_rate = self._capacity / self._window
        return deficit / refill_rate

    def consume(self, tokens: float) -> None:
        """tokens 차감 (보정용 음수 허용)."""
        self._refill()
        self._available -= tokens
        if self._available < 0:
            logger.debug(
                "[RateLimiter] %s 버킷 음수 진입 - available=%.0f",
                self._name,
                self._available,
            )

    def refund(self, tokens: float) -> None:
        """tokens 환불 (capacity 상한 준수)."""
        self._refill()
        self._available = min(self._capacity, self._available + tokens)

    def used_ratio(self) -> float:
        """현재 사용률 (0.0 ~ 1.0). soft limit 판정용."""
        self._refill()
        return max(0.0, 1.0 - self._available / self._capacity)


class RateLimiter:
    """TPM + TPD 이중 버킷 Rate Limiter."""

    def __init__(self, tpm_limit: int, tpd_limit: int) -> None:
        self._tpm = _TokenBucket(tpm_limit, 60.0, "TPM")
        self._tpd = _TokenBucket(tpd_limit, 24 * 60 * 60.0, "TPD")
        self._lock = asyncio.Lock()

    async def acquire(self, estimated_tokens: int) -> None:
        """두 버킷 모두 estimated_tokens 여유 확보까지 대기."""
        if estimated_tokens <= 0:
            return
        while True:
            async with self._lock:
                wait_tpm = self._tpm.wait_time(estimated_tokens)
                wait_tpd = self._tpd.wait_time(estimated_tokens)
                wait = max(wait_tpm, wait_tpd)
                if wait <= 0:
                    self._tpm.consume(estimated_tokens)
                    self._tpd.consume(estimated_tokens)
                    return
            logger.info(
                "[RateLimiter] 토큰 예산 대기 %.1f초 (est=%d, tpm_wait=%.1f, tpd_wait=%.1f)",
                wait,
                estimated_tokens,
                wait_tpm,
                wait_tpd,
            )
            await asyncio.sleep(wait)

    def tpd_used_ratio(self) -> float:
        """TPD 버킷 사용률. soft limit 판정용."""
        return self._tpd.used_ratio()

    def record_actual(self, actual_tokens: int, estimated_tokens: int) -> None:
        """실제 사용량과 추정치 차이 보정.

        - delta > 0: 추가 차감
        - delta < 0: 일부 환불
        """
        delta = actual_tokens - estimated_tokens
        if delta == 0:
            return
        if delta > 0:
            self._tpm.consume(delta)
            self._tpd.consume(delta)
        else:
            self._tpm.refund(-delta)
            self._tpd.refund(-delta)


_TIKTOKEN_ENCODER = None
_TIKTOKEN_FAILED = False


def _get_tiktoken_encoder():
    """tiktoken 인코더 지연 로드.

    - TIKTOKEN_DISABLE=1: 오프라인 테스트용 강제 비활성화
    - 정상: cl100k_base 1회 로드 후 캐시
    - 실패: None 반환 → 폴백 경로 유도
    """
    global _TIKTOKEN_ENCODER, _TIKTOKEN_FAILED
    if _TIKTOKEN_FAILED:
        return None
    if _TIKTOKEN_ENCODER is not None:
        return _TIKTOKEN_ENCODER
    import os

    if os.environ.get("TIKTOKEN_DISABLE") == "1":
        _TIKTOKEN_FAILED = True
        return None
    try:
        import tiktoken

        _TIKTOKEN_ENCODER = tiktoken.get_encoding("cl100k_base")
        return _TIKTOKEN_ENCODER
    except Exception as e:  # pragma: no cover
        logger.warning("[RateLimiter] tiktoken 로드 실패, 휴리스틱 폴백 사용: %s", e)
        _TIKTOKEN_FAILED = True
        return None


def estimate_prompt_tokens(text: str) -> int:
    """tiktoken 기반 프롬프트 토큰 사전 추정.

    - Qwen 토크나이저와 cl100k_base 불일치 무시 (record_actual 로 보정)
    - 로드 실패 / TIKTOKEN_DISABLE=1: len(text)//4 휴리스틱 폴백
    """
    enc = _get_tiktoken_encoder()
    if enc is None:
        return max(1, len(text) // 4)
    try:
        return len(enc.encode(text))
    except Exception:  # pragma: no cover
        return max(1, len(text) // 4)
