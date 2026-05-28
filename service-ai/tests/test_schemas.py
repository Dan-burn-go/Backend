"""app.models.schemas — Pydantic 모델 유효성 검증 테스트."""

from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.models.schemas import AnalysisResult, CongestionEvent


class TestCongestionEvent:
    def test_required_fields_ok(self):
        e = CongestionEvent(
            area_name="강남",
            area_code="POI001",
            congestion_level="BUSY",
            max_people_count=200,
            population_time="2026-05-28 09:00",
        )
        assert e.area_code == "POI001"
        assert e.avg_max_people is None
        assert e.ratio is None

    def test_optional_fields_accepted(self):
        e = CongestionEvent(
            area_name="홍대",
            area_code="POI002",
            congestion_level="NORMAL",
            max_people_count=50,
            population_time="2026-05-28 12:00",
            avg_max_people=120.5,
            ratio=0.42,
        )
        assert e.avg_max_people == 120.5
        assert e.ratio == pytest.approx(0.42)

    def test_missing_required_field_raises(self):
        with pytest.raises(ValidationError):
            CongestionEvent(  # type: ignore[call-arg]
                area_name="X",
                area_code="Y",
                congestion_level="BUSY",
                # max_people_count 누락
                population_time="2026-05-28 00:00",
            )

    def test_wrong_type_for_max_people_count_raises(self):
        with pytest.raises(ValidationError):
            CongestionEvent(
                area_name="X",
                area_code="Y",
                congestion_level="BUSY",
                max_people_count="not-an-int",  # type: ignore[arg-type]
                population_time="2026-05-28 00:00",
            )


class TestAnalysisResult:
    def test_all_fields_ok(self):
        r = AnalysisResult(
            area_name="잠실",
            area_code="POI003",
            congestion_level="VERY_BUSY",
            analysis_message="혼잡 원인 분석 결과입니다.",
            population_time="2026-05-28 18:00",
        )
        assert r.area_name == "잠실"
        assert r.analysis_message == "혼잡 원인 분석 결과입니다."

    def test_missing_field_raises(self):
        with pytest.raises(ValidationError):
            AnalysisResult(  # type: ignore[call-arg]
                area_name="X",
                area_code="Y",
                congestion_level="BUSY",
                # analysis_message 누락
                population_time="2026-05-28 00:00",
            )
