"""환각 방지 검증 헬퍼 단위 테스트.

운영 사고: 모델이 검색 결과에 5월 19일 일정이 없는데도 5월 19일 잠실 메시지를
"프로야구 및 프로농구 경기로 인한 혼잡"으로 합성한 사례 → 본문에 오늘 날짜가
명시되지 않으면 메시지를 강제로 안전 문구로 교체하도록 가드 추가.
"""

from __future__ import annotations

from datetime import date

from app.ai.openai.client import (
    _build_fallback_message,
    _has_today_marker,
    _is_safe_message,
    _today_date_variants,
    _validate_anti_hallucination,
)
from app.models.schemas import CongestionEvent


TODAY = date(2026, 5, 19)

_EVENT_NO_RATIO = CongestionEvent(
    area_code="POI001",
    area_name="잠실",
    congestion_level="BUSY",
    max_people_count=50000,
    population_time="2026-05-19 14:00",
)
_EVENT_WITH_RATIO = CongestionEvent(
    area_code="POI001",
    area_name="잠실",
    congestion_level="BUSY",
    max_people_count=50000,
    population_time="2026-05-19 14:00",
    ratio=2.3,
)


class TestTodayDateVariants:
    def test_includes_common_korean_news_formats(self) -> None:
        variants = _today_date_variants(TODAY)
        assert "2026-05-19" in variants
        assert "5월 19일" in variants
        assert "5/19" in variants
        assert "05-19" in variants

    def test_zero_padding_and_unpadded_both_present(self) -> None:
        variants = _today_date_variants(TODAY)
        # padded
        assert "05/19" in variants
        # unpadded
        assert "5-19" in variants


class TestHasTodayMarker:
    def test_returns_true_on_iso_format(self) -> None:
        text = "5월 19일 오후 7시 잠실구장에서 KBO 정규시즌 진행 (2026-05-19)"
        assert _has_today_marker(text, TODAY) is True

    def test_returns_true_on_korean_format(self) -> None:
        text = "이번 5월 19일 잠실에서 야구 경기가 열립니다"
        assert _has_today_marker(text, TODAY) is True

    def test_returns_false_when_only_other_dates_present(self) -> None:
        # 운영 사례: 검색 결과가 전부 3월 기사
        text = "2026-03-28 KBO 개막전 잠실 / 3월 29일 농구 경기"
        assert _has_today_marker(text, TODAY) is False

    def test_returns_false_on_empty_text(self) -> None:
        assert _has_today_marker("", TODAY) is False


class TestIsSafeMessage:
    def test_safe_when_contains_unknown_marker(self) -> None:
        assert _is_safe_message("원인 불명, 추가 모니터링 필요") is True

    def test_safe_when_contains_monitoring_marker(self) -> None:
        assert _is_safe_message("외부 이벤트 미확인, 추가 모니터링 진행") is True

    def test_safe_when_contains_unconfirmed_event_marker(self) -> None:
        assert _is_safe_message("평균 대비 2.3배 (외부 이벤트 미확인)") is True

    def test_unsafe_for_concrete_event_claim(self) -> None:
        assert _is_safe_message("프로야구 경기로 인한 혼잡") is False


class TestBuildFallbackMessage:
    def test_includes_ratio_when_present(self) -> None:
        msg = _build_fallback_message(_EVENT_WITH_RATIO)
        assert "2.3배" in msg
        assert "외부 이벤트 미확인" in msg

    def test_no_ratio_returns_generic(self) -> None:
        msg = _build_fallback_message(_EVENT_NO_RATIO)
        assert msg == "외부 이벤트 미확인, 추가 모니터링 필요"


class TestValidateAntiHallucination:
    def test_passes_through_when_tool_not_called(self) -> None:
        # 일반 분석(출퇴근 등)은 외부 이벤트 의존이 없으므로 검증 스킵
        out, replaced = _validate_anti_hallucination(
            "출근 시간대 업무지구 혼잡",
            tool_called=False,
            tool_results=None,
            today=TODAY,
            event=_EVENT_NO_RATIO,
        )
        assert out == "출근 시간대 업무지구 혼잡"
        assert replaced is False

    def test_passes_through_when_message_already_safe(self) -> None:
        out, replaced = _validate_anti_hallucination(
            "원인 불명, 추가 모니터링 필요",
            tool_called=True,
            tool_results=[{"title": "무관 기사", "body": "오래 전 일", "date": ""}],
            today=TODAY,
            event=_EVENT_NO_RATIO,
        )
        assert replaced is False
        assert "원인 불명" in out

    def test_passes_through_when_today_marker_in_body(self) -> None:
        out, replaced = _validate_anti_hallucination(
            "잠실 야구 경기로 인한 혼잡",
            tool_called=True,
            tool_results=[
                {
                    "title": "오늘 잠실 KBO 정규시즌 일정",
                    "date": "2026-05-19T11:00:00+00:00",
                    "body": "5월 19일 오후 7시 잠실구장 KBO 정규시즌 경기",
                },
            ],
            today=TODAY,
            event=_EVENT_NO_RATIO,
        )
        assert out == "잠실 야구 경기로 인한 혼잡"
        assert replaced is False

    def test_replaces_when_no_today_marker_in_body(self) -> None:
        # 운영 사고 재현: 검색 결과가 전부 3월 기사인데 모델은 5/19 메시지 합성
        out, replaced = _validate_anti_hallucination(
            "프로야구 및 프로농구 경기로 인한 혼잡",
            tool_called=True,
            tool_results=[
                {
                    "title": "주말 잠실종합운동장 6만 명 운집",
                    "date": "2026-03-29T09:02:00+00:00",
                    "body": "이번 주말 서울 잠실종합운동장에서 프로야구 개막전을 비롯해",
                },
                {
                    "title": "잠실 돔구장 착공 확정",
                    "date": "2026-03-11T18:40:00+00:00",
                    "body": "2032년 서울 송파구 잠실종합운동장 일대가",
                },
            ],
            today=TODAY,
            event=_EVENT_NO_RATIO,
        )
        assert out == _build_fallback_message(_EVENT_NO_RATIO)
        assert replaced is True

    def test_replaces_with_ratio_when_event_has_ratio(self) -> None:
        out, replaced = _validate_anti_hallucination(
            "프로야구 경기로 인한 혼잡",
            tool_called=True,
            tool_results=[],
            today=TODAY,
            event=_EVENT_WITH_RATIO,
        )
        assert "2.3배" in out
        assert "외부 이벤트 미확인" in out
        assert replaced is True

    def test_replaces_when_tool_results_empty(self) -> None:
        # 검색했지만 결과 0건 → 본문 0자 → 매칭 불가 → 안전 교체
        out, replaced = _validate_anti_hallucination(
            "행사로 인한 혼잡",
            tool_called=True,
            tool_results=[],
            today=TODAY,
            event=_EVENT_NO_RATIO,
        )
        assert out == _build_fallback_message(_EVENT_NO_RATIO)
        assert replaced is True
