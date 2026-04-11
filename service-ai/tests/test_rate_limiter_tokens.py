"""app.ai.rate_limiter — TPM/TPD 이중 버킷 테스트."""

from __future__ import annotations

import asyncio

import pytest

from app.ai.rate_limiter import RateLimiter, _TokenBucket, estimate_prompt_tokens


@pytest.mark.asyncio
async def test_acquire_fast_path_under_budget():
    limiter = RateLimiter(tpm_limit=1000, tpd_limit=10_000)
    # 여유 충분 → 즉시 반환
    await asyncio.wait_for(limiter.acquire(100), timeout=0.5)


@pytest.mark.asyncio
async def test_acquire_blocks_when_tpm_exhausted(monkeypatch):
    limiter = RateLimiter(tpm_limit=100, tpd_limit=1_000_000)
    # 전체 예산 소진
    await limiter.acquire(100)

    # 다음 요청 → 버킷 비어 대기 필요
    # asyncio.sleep 가로채 호출 여부만 확인
    sleep_calls: list[float] = []

    original_sleep = asyncio.sleep

    async def fake_sleep(delay):
        sleep_calls.append(delay)
        # 무한 루프 방지 → 버킷 즉시 충전
        limiter._tpm._available = 100.0
        limiter._tpd._available = 1_000_000.0
        await original_sleep(0)

    monkeypatch.setattr("app.ai.rate_limiter.asyncio.sleep", fake_sleep)

    await asyncio.wait_for(limiter.acquire(50), timeout=1.0)
    assert len(sleep_calls) >= 1
    assert sleep_calls[0] > 0  # 실제 대기 시간 계산 확인


def test_record_actual_adjusts_consumed_amount():
    limiter = RateLimiter(tpm_limit=1000, tpd_limit=10_000)
    limiter._tpm._available = 1000.0
    limiter._tpd._available = 10_000.0

    # 추정 100 / 실제 150 → 추가 50 차감
    limiter._tpm.consume(100)
    limiter._tpd.consume(100)
    limiter.record_actual(actual_tokens=150, estimated_tokens=100)
    # 초기 1000 - 100 - 50 = 850
    assert limiter._tpm._available == pytest.approx(850.0, abs=0.5)
    assert limiter._tpd._available == pytest.approx(9850.0, abs=0.5)


def test_record_actual_refunds_when_overestimated():
    limiter = RateLimiter(tpm_limit=1000, tpd_limit=10_000)
    limiter._tpm._available = 1000.0
    limiter._tpd._available = 10_000.0
    limiter._tpm.consume(300)
    limiter._tpd.consume(300)
    # 추정 300 / 실제 200 → 100 환불
    limiter.record_actual(actual_tokens=200, estimated_tokens=300)
    assert limiter._tpm._available == pytest.approx(800.0, abs=0.5)
    assert limiter._tpd._available == pytest.approx(9800.0, abs=0.5)


@pytest.mark.asyncio
async def test_acquire_blocks_when_tpd_exhausted(monkeypatch):
    """TPD 소진 시 TPM 여유 있어도 대기 필요."""
    limiter = RateLimiter(tpm_limit=1_000_000, tpd_limit=100)
    await limiter.acquire(100)

    sleep_calls: list[float] = []
    original_sleep = asyncio.sleep

    async def fake_sleep(delay):
        sleep_calls.append(delay)
        limiter._tpm._available = 1_000_000.0
        limiter._tpd._available = 100.0
        await original_sleep(0)

    monkeypatch.setattr("app.ai.rate_limiter.asyncio.sleep", fake_sleep)
    await asyncio.wait_for(limiter.acquire(50), timeout=1.0)
    assert sleep_calls and sleep_calls[0] > 0


def test_token_bucket_wait_time_calculation():
    bucket = _TokenBucket(capacity=60, window_seconds=60.0, name="T")
    bucket._available = 0.0
    # 누출률 = 60 / 60 = 1 token/s → 30 토큰 대기 = 30초
    wait = bucket.wait_time(30)
    assert wait == pytest.approx(30.0, abs=0.1)


def test_estimate_prompt_tokens_returns_positive():
    assert estimate_prompt_tokens("hello world") > 0
    assert estimate_prompt_tokens("") >= 0
