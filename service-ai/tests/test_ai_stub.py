"""app.ai.stub — StubAnalyzer 동작 테스트."""

from __future__ import annotations

import pytest

from app.ai.stub import StubAnalyzer
from app.models.schemas import CongestionEvent


def _event(code: str = "A", level: str = "BUSY") -> CongestionEvent:
    return CongestionEvent(
        area_name=f"지역-{code}",
        area_code=code,
        congestion_level=level,
        max_people_count=100,
        population_time="2026-05-28 12:00",
    )


class TestStubAnalyzer:
    @pytest.mark.asyncio
    async def test_normal_mode_returns_results_for_all_events(self):
        analyzer = StubAnalyzer()
        events = [_event("A"), _event("B"), _event("C")]
        results = await analyzer.analyze(events)
        assert len(results) == 3

    @pytest.mark.asyncio
    async def test_result_fields_match_event(self):
        analyzer = StubAnalyzer()
        e = _event("X", level="VERY_BUSY")
        results = await analyzer.analyze([e])
        r = results[0]
        assert r.area_name == e.area_name
        assert r.area_code == e.area_code
        assert r.congestion_level == e.congestion_level
        assert r.population_time == e.population_time

    @pytest.mark.asyncio
    async def test_normal_mode_message_contains_stub_tag(self):
        analyzer = StubAnalyzer()
        results = await analyzer.analyze([_event("A")])
        assert "[Stub]" in results[0].analysis_message
        assert "Stub-Anomaly" not in results[0].analysis_message

    @pytest.mark.asyncio
    async def test_anomaly_mode_message_contains_stub_anomaly_tag(self):
        analyzer = StubAnalyzer()
        results = await analyzer.analyze([_event("A")], mode="anomaly")
        assert "[Stub-Anomaly]" in results[0].analysis_message

    @pytest.mark.asyncio
    async def test_empty_events_returns_empty_list(self):
        analyzer = StubAnalyzer()
        results = await analyzer.analyze([])
        assert results == []

    @pytest.mark.asyncio
    async def test_close_does_not_raise(self):
        """AIAnalyzer.close() 기본 구현 — 예외 없음."""
        analyzer = StubAnalyzer()
        await analyzer.close()  # should not raise
