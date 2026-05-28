"""요청수 + 토큰 예산 기반 Rate Limiter.

- gpt-oss-120b (Cerebras, 2026-05 확인)
  · RPM 5 / TPM 30,000 / TPH 1,000,000 / TPD 1,000,000
- RPM 5(=12초당 1회)가 바인딩 제약 → 토큰만으로는 요청수 초과를 못 막음
- 설계: RPM + TPM + TPD 삼중 leaky bucket 으로 요청수·토큰 동시 강제
  · TPH 는 총량(1M)이 TPD 와 같아 별도 버킷 불필요 (TPD 버킷이 커버)

API
- await limiter.acquire(estimated_tokens): 세 버킷 여유 확보까지 대기 (요청 1건 + 토큰)
- limiter.record_actual(actual, estimated): 응답 수신 후 실제 토큰 사용량 보정
  · RPM 은 환불하지 않음 — 실패(429) 요청도 서버 RPM 카운터에 잡히므로
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
    """RPM + TPM + TPD 삼중 버킷 Rate Limiter."""

    def __init__(self, *, rpm_limit: int, tpm_limit: int, tpd_limit: int) -> None:
        self._rpm = _TokenBucket(rpm_limit, 60.0, "RPM")
        self._tpm = _TokenBucket(tpm_limit, 60.0, "TPM")
        self._tpd = _TokenBucket(tpd_limit, 24 * 60 * 60.0, "TPD")
        self._lock = asyncio.Lock()

    async def acquire(self, estimated_tokens: int) -> None:
        """세 버킷(요청 1건 + estimated_tokens) 모두 여유 확보까지 대기."""
        estimated_tokens = max(0, estimated_tokens)
        while True:
            async with self._lock:
                wait_rpm = self._rpm.wait_time(1)
                # 요청 토큰이 버킷 용량을 넘으면 wait_time 이 영원히 양수 -> 무한 hang.
                # 용량으로 clamp 해 "가득 차면 즉시 실행 후 음수 진입" 시킨다.
                wait_tpm = self._tpm.wait_time(min(estimated_tokens, int(self._tpm.capacity)))
                wait_tpd = self._tpd.wait_time(min(estimated_tokens, int(self._tpd.capacity)))
                wait = max(wait_rpm, wait_tpm, wait_tpd)
                if wait <= 0:
                    self._rpm.consume(1)
                    self._tpm.consume(estimated_tokens)
                    self._tpd.consume(estimated_tokens)
                    return
            logger.info(
                "[RateLimiter] 예산 대기 %.1f초 (est=%d, rpm_wait=%.1f, tpm_wait=%.1f, tpd_wait=%.1f)",
                wait,
                estimated_tokens,
                wait_rpm,
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
