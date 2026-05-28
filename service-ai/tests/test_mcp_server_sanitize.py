"""search_web query sanitize 회귀 방지 테스트.

- ISO/슬래시/점/한국식 날짜 토큰 제거
- 토큰 없는 쿼리는 변형 없음
- sanitize 결과가 빈 문자열이면 원본 반환
"""

from __future__ import annotations

import pytest

from app.ai.mcp.server import _sanitize_query


@pytest.mark.parametrize(
    "raw, expected",
    [
        # ISO 전체 포맷
        ("홍대 2026-05-26 콘서트", "홍대 콘서트"),
        ("강남 2026.05.26 시위", "강남 시위"),
        ("잠실 2026/05/26 행사", "잠실 행사"),
        # 짧은 형태 (월/일만)
        ("잠실 05/26 행사", "잠실 행사"),
        ("성수 05-26 팝업", "성수 팝업"),
        ("여의도 05.26 축제", "여의도 축제"),
        # 한국식 단위
        ("홍대 5월 26일 콘서트", "홍대 콘서트"),
        ("강남 2026년 행사", "강남 행사"),
        # 토큰 없음 — 변형 없음
        ("홍대 콘서트", "홍대 콘서트"),
        ("잠실 야구 경기", "잠실 야구 경기"),
    ],
)
def test_sanitize_strips_date_tokens(raw: str, expected: str) -> None:
    assert _sanitize_query(raw) == expected


def test_sanitize_empty_after_strip_falls_back_to_original() -> None:
    """결과가 빈 문자열이 되면 원본 query 그대로 사용."""
    raw = "2026-05-26"
    assert _sanitize_query(raw) == raw
