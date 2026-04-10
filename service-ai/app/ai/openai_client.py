import json
import logging
import re

import httpx

from app.ai.errors import NonRetriableError, RetriableError
from app.ai.interface import AIAnalyzer
from app.ai.rate_limiter import RateLimiter, estimate_prompt_tokens
from app.config import settings
from app.models.schemas import AnalysisResult, CongestionEvent

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "당신은 서울시 실시간 혼잡도 데이터를 분석하는 전문가입니다. "
    "각 지역의 혼잡 원인을 지역 특성(상권, 교통, 관광지 등)과 "
    "시간대 맥락(출근길, 점심시간, 퇴근길, 저녁 약속, 심야 등)을 고려하여 간결하게 분석하세요. "
    '응답은 반드시 {"results": [...]} 형태의 JSON 객체로, '
    "각 항목에 area_code, area_name, analysis_message 필드를 포함하세요."
)

# retry-after 헤더 누락 시 기본 대기 시간(초)
DEFAULT_RETRY_AFTER = 60.0


def _parse_retry_after(headers: httpx.Headers) -> float:
    """retry-after / x-ratelimit-reset-* 헤더에서 대기 시간 파싱.

    - Cerebras retry-after: 초 단위 (누락 가능)
    - 폴백: x-ratelimit-reset-tokens, x-ratelimit-reset-requests
    """
    for key in ("retry-after", "x-ratelimit-reset-tokens", "x-ratelimit-reset-requests"):
        raw = headers.get(key)
        if not raw:
            continue
        try:
            # "0.12s" 같은 suffix 제거
            cleaned = re.sub(r"[^0-9.]", "", raw)
            if not cleaned:
                continue
            return max(1.0, float(cleaned))
        except ValueError:
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


class OpenAIAnalyzer(AIAnalyzer):
    """OpenAI (Cerebras) API 호출로 혼잡 원인 분석."""

    def __init__(self) -> None:
        self._client = httpx.AsyncClient(
            base_url=settings.openai_base_url,
            headers={"Authorization": f"Bearer {settings.openai_api_key}"},
            timeout=httpx.Timeout(60.0, read=300.0),
        )
        self._rate_limiter = RateLimiter(
            tpm_limit=settings.tpm_limit,
            tpd_limit=settings.tpd_limit,
        )

    async def analyze(self, events: list[CongestionEvent]) -> list[AnalysisResult]:
        user_content = json.dumps(
            [e.model_dump() for e in events], ensure_ascii=False
        )
        user_message = f"{user_content}\n\n결과는 Markdown 없이 순수 JSON 객체로만 응답하세요."

        # 프롬프트 토큰 사전 추정
        # - 시스템 + 유저 메시지 + 응답 예산 경험치(배치당 약 400)
        estimated = (
            estimate_prompt_tokens(SYSTEM_PROMPT)
            + estimate_prompt_tokens(user_message)
            + 400
        )

        await self._rate_limiter.acquire(estimated)
        response = await self._client.post(
            "/chat/completions",
            json={
                "model": settings.openai_model,
                "messages": [
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": user_message},
                ],
                # Ollama/Qwen 호환: response_format 미지원 → 프롬프트로 JSON 출력 유도
            },
        )

        if response.status_code == 429:
            # 실패 요청은 토큰 미소비 → 추정치 전액 환불
            self._rate_limiter.record_actual(0, estimated)
            raise _classify_429(response)

        response.raise_for_status()

        payload = response.json()

        # 응답 수신 후 실제 사용량으로 보정
        usage = payload.get("usage") or {}
        actual_total = int(usage.get("total_tokens", estimated))
        self._rate_limiter.record_actual(actual_total, estimated)

        try:
            content = payload["choices"][0]["message"]["content"]
            # 마크다운 코드블록 제거 방어
            match = re.search(r'```(?:json)?\s*(.*?)```', content, re.DOTALL)
            if match:
                content = match.group(1)
            parsed = json.loads(content.strip())
            if isinstance(parsed, list):
                parsed = {"results": parsed}
            elif not isinstance(parsed, dict):
                parsed = {"results": []}
        except (KeyError, IndexError, json.JSONDecodeError) as e:
            logger.error("[OpenAI] 응답 파싱 실패 - %s", e)
            raise

        items = parsed.get("results", parsed.get("data", []))

        event_map = {e.area_code: e for e in events}
        results = []
        for item in items:
            area_code = item.get("area_code")
            analysis_message = item.get("analysis_message")
            if not area_code or not analysis_message:
                continue
            event = event_map.get(area_code)
            if event is None:
                continue
            results.append(
                AnalysisResult(
                    area_name=event.area_name,
                    area_code=area_code,
                    congestion_level=event.congestion_level,
                    analysis_message=analysis_message,
                    population_time=event.population_time,
                )
            )
        return results

    async def close(self) -> None:
        await self._client.aclose()
