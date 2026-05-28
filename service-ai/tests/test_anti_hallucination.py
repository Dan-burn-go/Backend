"""검색 결과의 '오늘 날짜' 판별 헬퍼 단위 테스트.

운영 사고: 모델이 검색 결과에 5월 19일 일정이 없는데도 5월 19일 잠실 메시지를
"프로야구 및 프로농구 경기로 인한 혼잡"으로 합성한 사례.
→ 검색 결과 본문에 오늘 날짜가 없으면 검색을 신뢰하지 않고 일반 분석으로 재생성.
"""

from __future__ import annotations

from datetime import date

from app.ai.openai.client import (
    _has_today_marker,
    _results_cover_today,
    _today_date_variants,
)


TODAY = date(2026, 5, 19)


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


class TestResultsCoverToday:
    def test_true_when_today_marker_in_body(self) -> None:
        results = [
            {
                "title": "오늘 잠실 KBO 정규시즌 일정",
                "date": "2026-05-19T11:00:00+00:00",
                "body": "5월 19일 오후 7시 잠실구장 KBO 정규시즌 경기",
            },
        ]
        assert _results_cover_today(results, TODAY) is True

    def test_true_when_today_marker_only_in_title(self) -> None:
        results = [{"title": "5월 19일 잠실 행사", "date": "", "body": ""}]
        assert _results_cover_today(results, TODAY) is True

    def test_false_when_only_other_dates(self) -> None:
        # 운영 사고 재현: 검색 결과가 전부 3월 기사
        results = [
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
        ]
        assert _results_cover_today(results, TODAY) is False

    def test_false_on_empty_results(self) -> None:
        assert _results_cover_today([], TODAY) is False
