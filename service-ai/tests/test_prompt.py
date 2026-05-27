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


class _FixedDateTime:
    """`datetime.now(KST)` 만 고정값으로 반환하는 가짜 datetime."""

    def __init__(self, fixed: datetime) -> None:
        self._fixed = fixed

    def now(self, tz=None):  # noqa: D401, ANN001 - signature 호환
        return self._fixed


class TestTodayWithWeekday:
    def test_weekday_label_for_weekday(self, monkeypatch) -> None:
        fixed = datetime(2026, 5, 27, 9, 0, tzinfo=_KST)  # 수요일
        monkeypatch.setattr(prompt_module, "datetime", _FixedDateTime(fixed))

        today, weekday = prompt_module._today_with_weekday()

        assert today == "2026-05-27"
        assert weekday == "수요일, 평일"

    def test_weekday_label_for_weekend(self, monkeypatch) -> None:
        fixed = datetime(2026, 5, 30, 12, 0, tzinfo=_KST)  # 토요일
        monkeypatch.setattr(prompt_module, "datetime", _FixedDateTime(fixed))

        today, weekday = prompt_module._today_with_weekday()

        assert today == "2026-05-30"
        assert weekday == "토요일, 주말"


class TestBuildSystemPrompt:
    def test_injects_today_and_weekday(self, monkeypatch) -> None:
        monkeypatch.setattr(
            prompt_module, "_today_with_weekday", lambda: ("2026-05-27", "수요일, 평일")
        )

        out = prompt_module.build_system_prompt()

        assert "현재 날짜: 2026-05-27 (수요일, 평일)" in out
        assert "search_web 호출 금지" in out


class TestBuildAnomalyPrompt:
    def test_injects_today_and_weekday(self, monkeypatch) -> None:
        monkeypatch.setattr(
            prompt_module, "_today_with_weekday", lambda: ("2026-05-27", "수요일, 평일")
        )

        out = prompt_module.build_anomaly_system_prompt()

        assert "현재 날짜: 2026-05-27 (수요일, 평일)" in out

    def test_includes_weekday_commute_escape_rule(self, monkeypatch) -> None:
        monkeypatch.setattr(
            prompt_module, "_today_with_weekday", lambda: ("2026-05-27", "수요일, 평일")
        )

        out = prompt_module.build_anomaly_system_prompt()

        assert "평일 출퇴근 일반 패턴, 외부 이벤트 미확인" in out
        assert "업무지구·교통허브" in out

    def test_today_marker_rule_uses_today_iso_only(self, monkeypatch) -> None:
        # body 매칭 룰의 {today} 는 weekday 라벨이 아닌 ISO 날짜만 들어가야 함
        monkeypatch.setattr(
            prompt_module, "_today_with_weekday", lambda: ("2026-05-27", "수요일, 평일")
        )

        out = prompt_module.build_anomaly_system_prompt()

        assert "body 에 오늘 날짜(2026-05-27)와 일치하는" in out

    def test_area_name_placeholder_is_literal(self, monkeypatch) -> None:
        # {area_name} 은 LLM 에게 placeholder 힌트 — escape 되어 리터럴로 남아야 함
        monkeypatch.setattr(
            prompt_module, "_today_with_weekday", lambda: ("2026-05-27", "수요일, 평일")
        )

        out = prompt_module.build_anomaly_system_prompt()

        assert "{area_name}" in out
