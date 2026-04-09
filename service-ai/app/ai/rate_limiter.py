import asyncio
import time
import logging

logger = logging.getLogger(__name__)


class RateLimiter:
    """토큰 버킷 기반 Rate Limiter.

    Cerebras Free Tier: qwen-3-235b → 30 RPM
    안전 마진을 두고 기본값 25 RPM 으로 설정.
    """

    def __init__(self, max_requests: int = 25, period_seconds: float = 60.0) -> None:
        self._max_tokens = max_requests
        self._period = period_seconds
        self._tokens = float(max_requests)
        self._last_refill = time.monotonic()
        self._lock = asyncio.Lock()

    async def acquire(self) -> None:
        """토큰을 1개 소비한다. 부족하면 충전될 때까지 대기."""
        while True:
            async with self._lock:
                self._refill()
                if self._tokens >= 1.0:
                    self._tokens -= 1.0
                    return
                wait = (1.0 - self._tokens) / (self._max_tokens / self._period)

            logger.info("[RateLimiter] 속도 제한 대기 %.1f초", wait)
            await asyncio.sleep(wait)

    def _refill(self) -> None:
        now = time.monotonic()
        elapsed = now - self._last_refill
        self._tokens = min(
            self._max_tokens,
            self._tokens + elapsed * (self._max_tokens / self._period),
        )
        self._last_refill = now
