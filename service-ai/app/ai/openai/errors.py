"""Cerebras 429 응답 분류 + retry-after 헤더 파싱."""

from __future__ import annotations

import json
import logging

import httpx

from app.ai.errors import NonRetriableError, RetriableError

logger = logging.getLogger(__name__)

# retry-after 헤더 누락 시 기본 대기 시간(초)
DEFAULT_RETRY_AFTER = 60.0


def _parse_retry_after(headers: httpx.Headers) -> float:
    """retry-after / x-ratelimit-reset-* 헤더에서 대기 시간(초) 파싱.

    지원 형식:
    - 정수/소수 초:   ``"60"``, ``"0.12"``
    - ``s`` 접미사:   ``"0.12s"``
    - ``ms`` 접미사:  ``"17ms"`` (밀리초 → 초 변환)

    파싱 실패(HTTP-Date, ``"1m30s"`` 등 알 수 없는 형식) → 해당 헤더 스킵 후
    다음 폴백 헤더로 이동. 모든 헤더 실패 시 :data:`DEFAULT_RETRY_AFTER` 반환.

    HTTP-Date(RFC 7231)는 의도적으로 지원하지 않습니다 — 잘못 파싱하여 수십억
    초 대기를 유발하느니 default 60초로 fall-back 하는 편이 안전합니다.
    """
    for key in ("retry-after", "x-ratelimit-reset-tokens", "x-ratelimit-reset-requests"):
        raw = headers.get(key)
        if not raw:
            continue
        raw = raw.strip()
        try:
            if raw.endswith("ms"):
                return max(1.0, float(raw[:-2]) / 1000.0)
            if raw.endswith("s"):
                return max(1.0, float(raw[:-1]))
            return max(1.0, float(raw))
        except ValueError:
            # HTTP-Date 등 알 수 없는 형식 → 다음 헤더로 fall-through
            continue
    return DEFAULT_RETRY_AFTER


def _extract_error_code(body_text: str) -> str:
    """429 응답 본문에서 error.code 추출. 실패 시 빈 문자열."""
    try:
        body = json.loads(body_text)
    except (json.JSONDecodeError, ValueError):
        return ""
    err = body.get("error") if isinstance(body, dict) else None
    if not isinstance(err, dict):
        return ""
    code = err.get("code") or err.get("type") or ""
    return str(code)


def _classify_429(response: httpx.Response) -> Exception:
    """429 응답 → RetriableError / NonRetriableError 분류."""
    headers = response.headers
    body_text = response.text or ""
    error_code = _extract_error_code(body_text)
    should_retry_raw = (headers.get("x-should-retry") or "").strip().lower()
    should_retry = should_retry_raw != "false"

    # 디버깅용 - 핵심 헤더 + 본문 로깅
    rl_headers = {
        k: v for k, v in headers.items()
        if "rate" in k.lower() or "retry" in k.lower()
    }
    logger.error(
        "[OpenAI] 429 Too Many Requests - code=%s, should_retry=%s, headers=%s, body=%s",
        error_code or "(unknown)",
        should_retry,
        rl_headers,
        body_text,
    )

    if error_code == "queue_exceeded" or not should_retry:
        return NonRetriableError(
            error_code=error_code or "non_retriable_429",
            message=body_text or "429 Too Many Requests (non-retriable)",
        )

    if error_code == "token_quota_exceeded":
        return RetriableError(
            error_code=error_code,
            message=body_text or "429 Too Many Requests (token quota)",
            retry_after=_parse_retry_after(headers),
        )

    # 알 수 없는 429 → 재시도 가능으로 간주 (기본 대기)
    return RetriableError(
        error_code=error_code or "unknown_429",
        message=body_text or "429 Too Many Requests",
        retry_after=_parse_retry_after(headers),
    )
