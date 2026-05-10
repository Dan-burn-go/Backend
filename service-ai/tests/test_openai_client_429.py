"""app.ai.openai.errors._classify_429 — 429 응답 분류 테스트.

- httpx.Response 직접 생성 → _classify_429 호출
- 네트워크/respx 없이 모든 분기 검증
"""

from __future__ import annotations

import json

import httpx
import pytest

from app.ai.errors import NonRetriableError, RetriableError
from app.ai.openai.errors import _classify_429, _extract_error_code, _parse_retry_after


def _make_response(
    body: dict | str,
    headers: dict | None = None,
) -> httpx.Response:
    body_bytes = body.encode() if isinstance(body, str) else json.dumps(body).encode()
    return httpx.Response(
        status_code=429,
        headers=headers or {},
        content=body_bytes,
    )


def test_queue_exceeded_is_non_retriable():
    # queue_exceeded → 헤더 retry=true 여도 NonRetriable
    response = _make_response(
        {"error": {"code": "queue_exceeded", "message": "queue full"}},
        headers={"x-should-retry": "true"},
    )
    err = _classify_429(response)
    assert isinstance(err, NonRetriableError)
    assert err.error_code == "queue_exceeded"


def test_x_should_retry_false_is_non_retriable_even_without_code():
    # x-should-retry: false → code 없어도 NonRetriable
    response = _make_response(
        {"error": {"message": "generic 429"}},
        headers={"x-should-retry": "false"},
    )
    err = _classify_429(response)
    assert isinstance(err, NonRetriableError)


def test_token_quota_exceeded_is_retriable_with_retry_after():
    response = _make_response(
        {"error": {"code": "token_quota_exceeded", "message": "tpm"}},
        headers={"retry-after": "7", "x-should-retry": "true"},
    )
    err = _classify_429(response)
    assert isinstance(err, RetriableError)
    assert err.error_code == "token_quota_exceeded"
    assert err.retry_after == pytest.approx(7.0)


def test_token_quota_exceeded_fallbacks_to_reset_header():
    # retry-after 누락 → x-ratelimit-reset-tokens 폴백
    response = _make_response(
        {"error": {"code": "token_quota_exceeded"}},
        headers={"x-ratelimit-reset-tokens": "12.3s"},
    )
    err = _classify_429(response)
    assert isinstance(err, RetriableError)
    assert err.retry_after == pytest.approx(12.3, abs=0.1)


def test_unknown_429_is_retriable_with_default_wait():
    response = _make_response("not even json", headers={})
    err = _classify_429(response)
    assert isinstance(err, RetriableError)
    assert err.retry_after > 0


def test_extract_error_code_handles_bad_json():
    assert _extract_error_code("") == ""
    assert _extract_error_code("not json") == ""
    assert _extract_error_code("[]") == ""  # list → error 필드 없음
    assert _extract_error_code('{"error": {"code": "foo"}}') == "foo"
    assert _extract_error_code('{"error": {"type": "bar"}}') == "bar"


def test_parse_retry_after_with_missing_headers():
    from app.ai.openai.errors import DEFAULT_RETRY_AFTER

    headers = httpx.Headers({})
    assert _parse_retry_after(headers) == DEFAULT_RETRY_AFTER


def test_parse_retry_after_plain_seconds():
    headers = httpx.Headers({"retry-after": "60"})
    assert _parse_retry_after(headers) == pytest.approx(60.0)


def test_parse_retry_after_with_s_suffix():
    headers = httpx.Headers({"retry-after": "0.12s"})
    # 0.12 < 1.0 → max clamp 적용
    assert _parse_retry_after(headers) == pytest.approx(1.0)

    headers = httpx.Headers({"retry-after": "12.3s"})
    assert _parse_retry_after(headers) == pytest.approx(12.3)


def test_parse_retry_after_with_ms_suffix():
    # 17ms = 0.017s → max clamp로 1.0초
    headers = httpx.Headers({"retry-after": "17ms"})
    assert _parse_retry_after(headers) == pytest.approx(1.0)

    # 5000ms = 5.0s
    headers = httpx.Headers({"x-ratelimit-reset-tokens": "5000ms"})
    assert _parse_retry_after(headers) == pytest.approx(5.0)


def test_parse_retry_after_rejects_http_date():
    """RFC 7231 HTTP-Date 형식은 잘못 파싱하느니 default로 fall-back."""
    from app.ai.openai.errors import DEFAULT_RETRY_AFTER

    # 표준 HTTP-Date — float() 실패해서 다음 헤더로 넘어가야 함 (catastrophic wait 방지)
    headers = httpx.Headers({"retry-after": "Wed, 21 Oct 2015 07:28:00 GMT"})
    assert _parse_retry_after(headers) == DEFAULT_RETRY_AFTER

    # HTTP-Date + 폴백 헤더가 유효 → 폴백 사용
    headers = httpx.Headers({
        "retry-after": "Wed, 21 Oct 2015 07:28:00 GMT",
        "x-ratelimit-reset-tokens": "8s",
    })
    assert _parse_retry_after(headers) == pytest.approx(8.0)


def test_parse_retry_after_rejects_compound_format():
    """'1m30s' 같은 복합 형식은 잘못 파싱하느니 default로 fall-back."""
    from app.ai.openai.errors import DEFAULT_RETRY_AFTER

    headers = httpx.Headers({"retry-after": "1m30s"})
    assert _parse_retry_after(headers) == DEFAULT_RETRY_AFTER


def test_parse_retry_after_strips_whitespace():
    headers = httpx.Headers({"retry-after": "  45  "})
    assert _parse_retry_after(headers) == pytest.approx(45.0)
