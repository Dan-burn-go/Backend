"""OpenAIAnalyzer._call_llm — 429 지수 백오프 재시도 테스트.

- _call_llm_once 를 AsyncMock 으로 교체해 429/성공 시퀀스 시뮬레이션
- asyncio.sleep 가로채 실제 대기 없이 백오프 지연값만 검증
"""

from __future__ import annotations

from unittest.mock import AsyncMock

import pytest

from app.ai.errors import NonRetriableError, RetriableError
from app.ai.openai import OpenAIAnalyzer
from app.config import settings


class _FakeMCP:
    def __init__(self) -> None:
        self.list_tools = AsyncMock(return_value=[])
        self.call_tool = AsyncMock()


@pytest.fixture
def analyzer():
    return OpenAIAnalyzer(_FakeMCP())


@pytest.fixture
def captured_sleeps(monkeypatch):
    sleeps: list[float] = []

    async def fake_sleep(delay):
        sleeps.append(delay)

    monkeypatch.setattr("app.ai.openai.client.asyncio.sleep", fake_sleep)
    return sleeps


@pytest.mark.asyncio
async def test_retries_on_retriable_then_succeeds(analyzer, captured_sleeps):
    analyzer._call_llm_once = AsyncMock(side_effect=[
        RetriableError("token_quota_exceeded", "tpm", retry_after=1.0),
        RetriableError("unknown_429", "again", retry_after=1.0),
        {"content": "ok"},
    ])

    msg = await analyzer._call_llm([{"role": "user", "content": "x"}], None)

    assert msg == {"content": "ok"}
    assert analyzer._call_llm_once.await_count == 3
    # 지수 증가: base*2**0=2, base*2**1=4 (retry_after=1 보다 큼)
    assert captured_sleeps == [
        pytest.approx(settings.llm_retry_base_delay),
        pytest.approx(settings.llm_retry_base_delay * 2),
    ]


@pytest.mark.asyncio
async def test_retry_after_floor_overrides_short_backoff(analyzer, captured_sleeps):
    # retry_after 가 지수 지연보다 크면 그 값을 따른다
    big = settings.llm_retry_base_delay + 50.0
    analyzer._call_llm_once = AsyncMock(side_effect=[
        RetriableError("token_quota_exceeded", "tpm", retry_after=big),
        {"content": "ok"},
    ])

    await analyzer._call_llm([{"role": "user", "content": "x"}], None)

    # max_delay 상한 적용 후 retry_after floor 비교
    expected = max(min(settings.llm_retry_base_delay, settings.llm_retry_max_delay), big)
    assert captured_sleeps == [pytest.approx(expected)]


@pytest.mark.asyncio
async def test_non_retriable_is_not_retried(analyzer, captured_sleeps):
    analyzer._call_llm_once = AsyncMock(
        side_effect=NonRetriableError("queue_exceeded", "full")
    )

    with pytest.raises(NonRetriableError):
        await analyzer._call_llm([{"role": "user", "content": "x"}], None)

    assert analyzer._call_llm_once.await_count == 1
    assert captured_sleeps == []


@pytest.mark.asyncio
async def test_exhausts_retries_then_raises(analyzer, captured_sleeps):
    analyzer._call_llm_once = AsyncMock(
        side_effect=RetriableError("unknown_429", "always", retry_after=0.0)
    )

    with pytest.raises(RetriableError):
        await analyzer._call_llm([{"role": "user", "content": "x"}], None)

    assert analyzer._call_llm_once.await_count == settings.llm_retry_max_attempts
    assert len(captured_sleeps) == settings.llm_retry_max_attempts - 1
