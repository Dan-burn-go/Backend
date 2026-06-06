"""시스템 프롬프트 빌더 단위 테스트.

- 오늘 날짜 + 요일/평일·주말 주입 확인
- anomaly 프롬프트의 평일 출퇴근 탈출구 룰 포함 확인
- format placeholder 누락으로 인한 KeyError 회귀 가드
"""

from __future__ import annotations

from datetime import datetime
from zoneinfo import ZoneInfo

from app.ai.openai import prompt as prompt_module


_KST = ZoneInfo("Asia/Seoul")
_WED = datetime(2026, 5, 27, 9, 0, tzinfo=_KST)   # 수요일
_SAT = datetime(2026, 5, 30, 12, 0, tzinfo=_KST)  # 토요일


class TestTodayWithWeekday:
    def test_weekday_label_for_weekday(self) -> None:
        today, weekday = prompt_module._today_with_weekday(_WED)

        assert today == "2026-05-27"
        assert weekday == "수요일, 평일"

    def test_weekday_label_for_weekend(self) -> None:
        today, weekday = prompt_module._today_with_weekday(_SAT)

        assert today == "2026-05-30"
        assert weekday == "토요일, 주말"


class TestBuildSystemPrompt:
    def test_injects_today_and_weekday(self) -> None:
        out = prompt_module.build_system_prompt(_WED)

        assert "현재 날짜: 2026-05-27 (수요일, 평일)" in out
        assert "search_web 호출 금지" in out


class TestBuildAnomalyPrompt:
    def test_injects_today_and_weekday(self) -> None:
        out = prompt_module.build_anomaly_system_prompt(_WED)

        assert "현재 날짜: 2026-05-27 (수요일, 평일)" in out

    def test_includes_weekday_commute_escape_rule(self) -> None:
        out = prompt_module.build_anomaly_system_prompt(_WED)

        assert "평일 출퇴근 통근 인파로 보입니다" in out
        assert "업무지구·교통허브" in out

    def test_no_unknown_cause_phrasing(self) -> None:
        # "원인 불명" / "미확인" 류 표현은 리포트에 노출하지 않는다
        out = prompt_module.build_anomaly_system_prompt(_WED)

        assert "미확인" not in out
        assert "원인 불명" not in out

    def test_today_marker_rule_uses_today_iso_only(self) -> None:
        # body 매칭 룰의 {today} 는 weekday 라벨이 아닌 ISO 날짜만 들어가야 함
        out = prompt_module.build_anomaly_system_prompt(_WED)

        assert "body 에 오늘 날짜(2026-05-27)와 일치하는" in out

    def test_area_name_placeholder_is_literal(self) -> None:
        # {area_name} 은 LLM 에게 placeholder 힌트 — escape 되어 리터럴로 남아야 함
        out = prompt_module.build_anomaly_system_prompt(_WED)

        assert "{area_name}" in out

    def test_event_timing_rules_present(self) -> None:
        # 미래/시점 부적절 행사 귀속 차단 + 복합 원인 허용 규칙
        out = prompt_module.build_anomaly_system_prompt(_WED)

        assert "[행사 시점·복합 원인 판단 — 엄격]" in out
        assert "population_time 을 '현재 시각'으로 본다" in out
        assert "행사 일시가 오늘(2026-05-27)이 아니면" in out
        assert "3시간 전부터" in out
        assert "종료 후 1~2시간" in out
        assert "둘 이상이면" in out
