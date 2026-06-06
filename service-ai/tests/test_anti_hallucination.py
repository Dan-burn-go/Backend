"""검색 결과의 '오늘 날짜' 판별 헬퍼 단위 테스트.

운영 사고: 모델이 검색 결과에 5월 19일 일정이 없는데도 5월 19일 잠실 메시지를
"프로야구 및 프로농구 경기로 인한 혼잡"으로 합성한 사례.
→ 검색 결과 본문에 오늘 날짜가 없으면 검색을 신뢰하지 않고 일반 분석으로 재생성.
"""

from __future__ import annotations

from datetime import date

from app.ai.openai.client import (
    _has_today_marker,
    _parse_pub_date,
    _result_date_recent,
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

    def test_true_when_pubdate_today_but_body_omits_month(self) -> None:
        # 운영 사고 재현: 오늘 발행 집회 기사인데 본문이 '19일'처럼 월을 생략해
        # 본문 토큰 매칭은 실패하지만 발행일로 통과해야 한다.
        results = [
            {
                "title": "재선거 요구 잠실 개표소 앞 3000명 집결",
                "date": "2026-05-19T04:52:59+00:00",
                "body": "19일 연합뉴스에 따르면 이날 낮 12시 잠실 일대에 2000명쯤 집결",
            },
        ]
        assert _has_today_marker(results[0]["body"], TODAY) is False
        assert _results_cover_today(results, TODAY) is True

    def test_true_when_relative_pubdate(self) -> None:
        # DDG yahoo/bing 백엔드가 뱉는 상대표현 — 본문엔 날짜 없음
        results = [
            {
                "title": "올림픽공원 개표소 봉쇄 집회",
                "date": "Opinion15 hours ago",
                "body": "재선거 요구 시위대가 핸드볼경기장 일대에 집결",
            },
        ]
        assert _results_cover_today(results, TODAY) is True


class TestParsePubDate:
    def test_iso_utc_converted_to_kst(self) -> None:
        # 04:52 UTC == 13:52 KST 같은 날
        assert _parse_pub_date("2026-05-19T04:52:59+00:00", TODAY) == date(2026, 5, 19)

    def test_iso_kst_boundary(self) -> None:
        # 2026-05-19 16:00 UTC == 2026-05-20 01:00 KST
        assert _parse_pub_date("2026-05-19T16:00:00+00:00", TODAY) == date(2026, 5, 20)

    def test_relative_hours_ago_maps_to_today(self) -> None:
        assert _parse_pub_date("15 hours ago", TODAY) == TODAY
        assert _parse_pub_date("3시간 전", TODAY) == TODAY

    def test_relative_hours_over_24_convert_to_days(self) -> None:
        # 24h 이상은 일 단위 환산 — today 로 오판해 가드 우회하면 안 됨
        assert _parse_pub_date("36 hours ago", TODAY) == date(2026, 5, 18)
        assert _parse_pub_date("48 hours ago", TODAY) == date(2026, 5, 17)
        assert _parse_pub_date("72시간 전", TODAY) == date(2026, 5, 16)

    def test_relative_days_ago(self) -> None:
        assert _parse_pub_date("2 days ago", TODAY) == date(2026, 5, 17)
        assert _parse_pub_date("1일 전", TODAY) == date(2026, 5, 18)

    def test_today_yesterday_words(self) -> None:
        assert _parse_pub_date("today", TODAY) == TODAY
        assert _parse_pub_date("어제", TODAY) == date(2026, 5, 18)

    def test_none_on_empty_or_unparsable(self) -> None:
        assert _parse_pub_date("", TODAY) is None
        assert _parse_pub_date("not-a-date", TODAY) is None

    def test_absolute_korean_date_not_parsed_as_relative(self) -> None:
        # ago/전 접미사 없는 절대날짜는 상대표현으로 오파싱되면 안 된다.
        # "5월 1일"의 '1일'을 today-1 로 오판하면 옛 기사가 최근으로 둔갑(가드 우회).
        assert _parse_pub_date("2026년 5월 19일", TODAY) is None
        assert _parse_pub_date("5월 1일", TODAY) is None


class TestResultDateRecent:
    def test_recent_within_window(self) -> None:
        assert _result_date_recent("2026-05-18T00:00:00+00:00", TODAY) is True  # 1일 전
        assert _result_date_recent("15 hours ago", TODAY) is True

    def test_old_article_rejected(self) -> None:
        # 원래 사고: 두 달 전 기사
        assert _result_date_recent("2026-03-29T09:02:00+00:00", TODAY) is False

    def test_unparsable_rejected(self) -> None:
        assert _result_date_recent("", TODAY) is False
        assert _result_date_recent("garbage", TODAY) is False
