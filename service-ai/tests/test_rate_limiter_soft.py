"""app.ai.rate_limiter — TPD soft limit 사용률 조회 테스트."""

from __future__ import annotations

import pytest

from app.ai.rate_limiter import RateLimiter


def test_tpd_used_ratio_initial_is_zero():
    limiter = RateLimiter(tpm_limit=1000, tpd_limit=10_000)
    assert limiter.tpd_used_ratio() == pytest.approx(0.0)


@pytest.mark.asyncio
async def test_tpd_used_ratio_after_acquire():
    limiter = RateLimiter(tpm_limit=1_000_000, tpd_limit=10_000)
    await limiter.acquire(2_000)
    # 2000 / 10000 = 0.2
    assert limiter.tpd_used_ratio() == pytest.approx(0.2, abs=0.01)


@pytest.mark.asyncio
async def test_tpd_used_ratio_crosses_soft_limit():
    """soft limit 80% 임계 동작 검증."""
    limiter = RateLimiter(tpm_limit=1_000_000, tpd_limit=1_000)
    soft_limit = 0.8

    # 70% 사용 → 임계 미만
    await limiter.acquire(700)
    assert limiter.tpd_used_ratio() < soft_limit

    # 추가 200 사용 → 90% → 임계 초과
    await limiter.acquire(200)
    assert limiter.tpd_used_ratio() >= soft_limit


def test_tpd_used_ratio_clamped_to_zero_when_overrefunded():
    """over-refund(잔여 > capacity)는 음수 사용률 대신 0.0 으로 클램프."""
    limiter = RateLimiter(tpm_limit=1000, tpd_limit=1000)
    # capacity 초과 환불해도 ratio 는 0 미만으로 안 떨어짐
    limiter._tpd._available = 2000.0
    assert limiter.tpd_used_ratio() == 0.0
