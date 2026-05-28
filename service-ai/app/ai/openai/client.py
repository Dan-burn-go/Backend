"""OpenAIAnalyzer — Cerebras API + MCP tool calling 으로 혼잡 원인 분석.

- max_hops=1: LLM round trip 최대 2회 (initial → tool result → final)
- 검색이 오늘 일정을 확증 못 하면 tool 미사용 일반 분석으로 재생성 (환각 차단)
- parallel tool calls 지원
- TPD soft limit 도달 시 tools 미주입
- 분석 1건당 구조화 INFO 로그 (Grafana → 외부 LLM 채점)
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
from datetime import date, datetime
from typing import Any
from zoneinfo import ZoneInfo

import httpx

from app.ai.errors import RetriableError
from app.ai.interface import AIAnalyzer
from app.ai.mcp.client import MCPClient
from app.ai.openai.errors import _classify_429
from app.ai.openai.prompt import build_anomaly_system_prompt, build_system_prompt
from app.ai.rate_limiter import RateLimiter, estimate_prompt_tokens
from app.config import settings
from app.models.schemas import AnalysisResult, CongestionEvent

logger = logging.getLogger(__name__)

# 배치당 응답 토큰 예산 경험치 (rate limiter 사전 추정용)
RESPONSE_TOKEN_BUDGET = 400

_KST = ZoneInfo("Asia/Seoul")


def _now_kst_date() -> date:
    """KST 기준 오늘 날짜. 테스트에서 monkeypatch로 고정 가능."""
    return datetime.now(_KST).date()


def _parse_ref_datetime(events: list[CongestionEvent]) -> datetime | None:
    """이벤트 population_time 을 KST datetime 으로 파싱. 실패 시 None.

    DLQ 재처리로 처리 시각과 이벤트 발생 시각의 날짜가 달라질 때, 프롬프트의
    '현재 날짜'와 검증 기준 today 를 이벤트 발생일에 맞춘다.
    """
    if not events:
        return None
    try:
        dt = datetime.fromisoformat(events[0].population_time)
        return dt.replace(tzinfo=dt.tzinfo or _KST)
    except (ValueError, TypeError):
        return None


def _today_date_variants(today: date) -> list[str]:
    """오늘 날짜를 한국 뉴스 본문에서 마주칠 만한 표기 변형 목록.

    한 변형이라도 등장하면 '검색 결과가 오늘 일정을 다룬다'고 본다.
    """
    y, m, d = today.year, today.month, today.day
    return [
        f"{y}-{m:02d}-{d:02d}",
        f"{y}.{m:02d}.{d:02d}",
        f"{y}/{m:02d}/{d:02d}",
        f"{y}년 {m}월 {d}일",
        f"{y}년{m}월{d}일",
        f"{m}월 {d}일",
        f"{m}/{d}",
        f"{m}-{d}",
        f"{m}.{d}",
        f"{m:02d}/{d:02d}",
        f"{m:02d}-{d:02d}",
    ]


def _has_today_marker(text: str, today: date) -> bool:
    return any(v in text for v in _today_date_variants(today))


def _results_cover_today(tool_results: list[dict[str, Any]], today: date) -> bool:
    """검색 결과(title+body) 합본에 오늘 날짜 표기가 하나라도 있으면 True.

    있으면 검색이 오늘 일정을 다룬 것으로 보고 모델 응답을 신뢰한다.
    없으면 모델이 과거 기사로 사건을 합성했을 수 있으므로 신뢰하지 않는다.
    """
    haystack = " ".join(
        part
        for r in tool_results
        for part in (r.get("body") or "", r.get("title") or "")
    )
    return _has_today_marker(haystack, today)


class OpenAIAnalyzer(AIAnalyzer):
    def __init__(self, mcp_client: MCPClient) -> None:
        self._client = httpx.AsyncClient(
            base_url=settings.openai_base_url,
            headers={"Authorization": f"Bearer {settings.openai_api_key}"},
            timeout=httpx.Timeout(60.0, read=300.0),
        )
        self._rate_limiter = RateLimiter(
            rpm_limit=settings.rpm_limit,
            tpm_limit=settings.tpm_limit,
            tpd_limit=settings.tpd_limit,
        )
        self._mcp_client = mcp_client

    async def analyze(
        self,
        events: list[CongestionEvent],
        *,
        mode: str = "normal",
    ) -> list[AnalysisResult]:
        is_anomaly = mode == "anomaly"
        ref_dt = _parse_ref_datetime(events)
        system_prompt = (
            build_anomaly_system_prompt(ref_dt) if is_anomaly
            else build_system_prompt(ref_dt)
        )
        user_content = json.dumps(
            [e.model_dump() for e in events], ensure_ascii=False
        )
        user_message = f"{user_content}\n\n결과는 Markdown 없이 순수 JSON 객체로만 응답하세요."

        messages: list[dict[str, Any]] = [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_message},
        ]

        # TPD soft limit 도달 시 tools 미주입 (기본 분석만)
        tools_disabled = (
            self._rate_limiter.tpd_used_ratio() >= settings.tpd_soft_limit_ratio
        )
        tools = None if tools_disabled else await self._mcp_client.list_tools()
        if tools_disabled:
            logger.warning(
                "[OpenAI] TPD soft limit 도달 - tools 비활성, used_ratio=%.2f",
                self._rate_limiter.tpd_used_ratio(),
            )

        # anomaly 모드 + tools 사용 가능 → tool_choice='required' 로 강제 호출
        tool_choice = "required" if (is_anomaly and not tools_disabled and tools) else None

        # hop 1
        message = await self._call_llm(messages, tools, tool_choice=tool_choice)
        tool_calls = message.get("tool_calls") or []

        # tool calls 있으면 실행 후 hop 2 (max_hops=1 고정)
        tool_called = False
        tool_queries: list[str] = []
        tool_results_flat: list[dict[str, Any]] = []
        if tool_calls and not tools_disabled:
            tool_called = True
            messages.append({
                "role": "assistant",
                "content": message.get("content"),
                "tool_calls": tool_calls,
            })
            parsed_calls: list[tuple[dict[str, Any], dict[str, Any]]] = []
            for tc in tool_calls:
                try:
                    args = json.loads(tc["function"].get("arguments") or "{}")
                except json.JSONDecodeError:
                    args = {}
                parsed_calls.append((tc, args))
            tool_results = await asyncio.gather(*[
                self._mcp_client.call_tool(tc["function"]["name"], args)
                for tc, args in parsed_calls
            ])
            for (tc, args), result in zip(parsed_calls, tool_results):
                messages.append({
                    "role": "tool",
                    "tool_call_id": tc["id"],
                    "content": result,
                })
                if args.get("query"):
                    tool_queries.append(str(args["query"]))
                try:
                    parsed_result = json.loads(result)
                    for r in parsed_result.get("results") or []:
                        tool_results_flat.append({
                            "title": r.get("title", ""),
                            "date": r.get("date", ""),
                            "body": r.get("body", ""),
                        })
                except json.JSONDecodeError:
                    pass
            # hop 2 — tools 미주입 (가드레일 #1: round trip 2회 고정)
            message = await self._call_llm(messages, tools=None)

        # 검색은 했지만 결과 본문에 오늘(이벤트 발생일) 날짜가 없으면(외부 이벤트 확증 실패)
        # tool_call 이 없던 것처럼 일반 분석으로 재생성한다.
        # 모델이 과거 기사로 사건을 합성하는 환각을 막고, 일반 패턴 원인을 직접 작성하게 함.
        ref_date = ref_dt.date() if ref_dt else _now_kst_date()
        if tool_called and not _results_cover_today(tool_results_flat, ref_date):
            logger.warning(
                "[OpenAI] 검색 결과에 오늘 날짜 없음 - 일반 분석으로 재생성 (events=%d)",
                len(events),
            )
            general_messages: list[dict[str, Any]] = [
                {"role": "system", "content": build_system_prompt(ref_dt)},
                {"role": "user", "content": user_message},
            ]
            message = await self._call_llm(general_messages, tools=None)
            return self._parse_results(message.get("content") or "", events)

        return self._parse_results(
            message.get("content") or "",
            events,
            tool_called=tool_called,
            tool_queries=tool_queries,
            tool_results=tool_results_flat,
        )

    async def _call_llm(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]] | None,
        *,
        tool_choice: str | None = None,
    ) -> dict[str, Any]:
        """429 지수 백오프 재시도 래퍼.

        - RetriableError: 대기 후 재시도. 대기 = max(base*2**(n-1), retry_after), 상한 max_delay
        - 재시도 한도 소진 시 RetriableError 전파 → 상위(batch)에서 DLQ
        - NonRetriableError: 재시도 없이 즉시 전파 → DLQ
        - 각 재시도는 acquire 를 다시 거치므로 RPM 버킷이 추가로 throttle
        """
        attempt = 1
        while True:
            try:
                return await self._call_llm_once(
                    messages, tools, tool_choice=tool_choice
                )
            except RetriableError as e:
                if attempt >= settings.llm_retry_max_attempts:
                    logger.error(
                        "[OpenAI] 429 재시도 한도 소진 (%d/%d) → DLQ 전파 - %s",
                        attempt,
                        settings.llm_retry_max_attempts,
                        e,
                    )
                    raise
                delay = min(
                    settings.llm_retry_base_delay * (2 ** (attempt - 1)),
                    settings.llm_retry_max_delay,
                )
                delay = max(delay, e.retry_after)
                logger.warning(
                    "[OpenAI] 429 재시도 %d/%d - %.1f초 대기 (retry_after=%.1f, code=%s)",
                    attempt,
                    settings.llm_retry_max_attempts,
                    delay,
                    e.retry_after,
                    e.error_code,
                )
                await asyncio.sleep(delay)
                attempt += 1

    async def _call_llm_once(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]] | None,
        *,
        tool_choice: str | None = None,
    ) -> dict[str, Any]:
        """LLM 1회 호출. RateLimiter acquire/record + 429 분류.

        실패 경로에서도 finally 로 추정치 환불 (토큰 누수 방지).
        """
        estimated = self._estimate_tokens(messages, tools)
        await self._rate_limiter.acquire(estimated)
        recorded = False
        try:
            body: dict[str, Any] = {
                "model": settings.openai_model,
                "messages": messages,
            }
            if tools:
                body["tools"] = tools
                if tool_choice is not None:
                    body["tool_choice"] = tool_choice

            response = await self._client.post("/chat/completions", json=body)

            if response.status_code == 429:
                self._rate_limiter.record_actual(0, estimated)
                recorded = True
                raise _classify_429(response)

            response.raise_for_status()
            payload = response.json()

            usage = payload.get("usage") or {}
            actual_total = int(usage.get("total_tokens", estimated))
            self._rate_limiter.record_actual(actual_total, estimated)
            recorded = True

            try:
                return payload["choices"][0]["message"]
            except (KeyError, IndexError) as e:
                logger.error("[OpenAI] 응답 파싱 실패 - %s", e)
                raise
        finally:
            if not recorded:
                self._rate_limiter.record_actual(0, estimated)

    def _estimate_tokens(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]] | None,
    ) -> int:
        """messages + tools schema 기반 사전 토큰 추정."""
        total = 0
        for m in messages:
            content = m.get("content")
            if isinstance(content, str):
                total += estimate_prompt_tokens(content)
            tc = m.get("tool_calls")
            if tc:
                total += estimate_prompt_tokens(
                    json.dumps(tc, ensure_ascii=False)
                )
        if tools:
            total += estimate_prompt_tokens(
                json.dumps(tools, ensure_ascii=False)
            )
        return total + RESPONSE_TOKEN_BUDGET

    def _parse_results(
        self,
        content: str,
        events: list[CongestionEvent],
        *,
        tool_called: bool = False,
        tool_queries: list[str] | None = None,
        tool_results: list[dict[str, Any]] | None = None,
    ) -> list[AnalysisResult]:
        """LLM 최종 응답 → AnalysisResult 리스트 + 구조화 로그."""
        if not content.strip():
            logger.warning(
                "[OpenAI] 빈 LLM 응답 - 분석 결과 0건으로 처리 (events=%d, tool_called=%s)",
                len(events), tool_called,
            )
            return []

        match = re.search(r'```(?:json)?\s*(.*?)```', content, re.DOTALL)
        if match:
            content = match.group(1)
        try:
            parsed = json.loads(content.strip())
        except (json.JSONDecodeError, ValueError) as e:
            logger.error(
                "[OpenAI] 응답 파싱 실패 - %s | content_len=%d, preview=%r",
                e, len(content), content[:200],
            )
            raise

        if isinstance(parsed, list):
            parsed = {"results": parsed}
        elif not isinstance(parsed, dict):
            parsed = {"results": []}

        items = parsed.get("results", parsed.get("data", []))
        event_map = {e.area_code: e for e in events}
        results: list[AnalysisResult] = []
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
            self._log_analysis(
                event=event,
                analysis_message=analysis_message,
                tool_called=tool_called,
                tool_queries=tool_queries,
                tool_results=tool_results,
            )
        return results

    def _log_analysis(
        self,
        *,
        event: CongestionEvent,
        analysis_message: str,
        tool_called: bool,
        tool_queries: list[str] | None,
        tool_results: list[dict[str, Any]] | None,
    ) -> None:
        """분석 결과 1건당 구조화 INFO 로그.

        Grafana Log Stream → 외부 LLM 채점 워크플로 입력용.
        tool_called=true 일 때만 tool_queries/tool_results 포함 (비대칭 길이).
        """
        log_data: dict[str, Any] = {
            "area_code": event.area_code,
            "area_name": event.area_name,
            "time": event.population_time,
            "level": event.congestion_level,
            "max_people": event.max_people_count,
            "tool_called": tool_called,
            "message": analysis_message,
        }
        if tool_called:
            log_data["tool_queries"] = tool_queries or []
            log_data["tool_results"] = tool_results or []
        logger.info("[Analysis] %s", json.dumps(log_data, ensure_ascii=False))

    async def close(self) -> None:
        await self._client.aclose()
