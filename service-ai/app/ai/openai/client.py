"""OpenAIAnalyzer — Cerebras API + MCP tool calling 으로 혼잡 원인 분석.

- max_hops=1: LLM round trip 최대 2회 (initial → tool result → final)
- parallel tool calls 지원
- TPD soft limit 도달 시 tools 미주입
- 분석 1건당 구조화 INFO 로그 (Grafana → 외부 LLM 채점)
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
from typing import Any

import httpx

from app.ai.interface import AIAnalyzer
from app.ai.mcp.client import MCPClient
from app.ai.openai.errors import _classify_429
from app.ai.openai.prompt import build_system_prompt
from app.ai.rate_limiter import RateLimiter, estimate_prompt_tokens
from app.config import settings
from app.models.schemas import AnalysisResult, CongestionEvent

logger = logging.getLogger(__name__)

# 배치당 응답 토큰 예산 경험치 (rate limiter 사전 추정용)
RESPONSE_TOKEN_BUDGET = 400


class OpenAIAnalyzer(AIAnalyzer):
    def __init__(self, mcp_client: MCPClient) -> None:
        self._client = httpx.AsyncClient(
            base_url=settings.openai_base_url,
            headers={"Authorization": f"Bearer {settings.openai_api_key}"},
            timeout=httpx.Timeout(60.0, read=300.0),
        )
        self._rate_limiter = RateLimiter(
            tpm_limit=settings.tpm_limit,
            tpd_limit=settings.tpd_limit,
        )
        self._mcp_client = mcp_client

    async def analyze(self, events: list[CongestionEvent]) -> list[AnalysisResult]:
        system_prompt = build_system_prompt()
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

        # hop 1
        message = await self._call_llm(messages, tools)
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
            tool_results = await asyncio.gather(*[
                self._mcp_client.call_tool(
                    tc["function"]["name"],
                    json.loads(tc["function"].get("arguments") or "{}"),
                )
                for tc in tool_calls
            ])
            for tc, result in zip(tool_calls, tool_results):
                messages.append({
                    "role": "tool",
                    "tool_call_id": tc["id"],
                    "content": result,
                })
                # 로그용 컨텍스트 누적
                try:
                    args = json.loads(tc["function"].get("arguments") or "{}")
                    if args.get("query"):
                        tool_queries.append(str(args["query"]))
                except json.JSONDecodeError:
                    pass
                try:
                    parsed_result = json.loads(result)
                    for r in parsed_result.get("results") or []:
                        tool_results_flat.append({
                            "title": r.get("title", ""),
                            "date": r.get("date", ""),
                        })
                except json.JSONDecodeError:
                    pass
            # hop 2 — tools 미주입 (가드레일 #1: round trip 2회 고정)
            message = await self._call_llm(messages, tools=None)

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
    ) -> dict[str, Any]:
        """LLM 1회 호출. RateLimiter acquire/record + 429 분류."""
        estimated = self._estimate_tokens(messages, tools)
        await self._rate_limiter.acquire(estimated)

        body: dict[str, Any] = {
            "model": settings.openai_model,
            "messages": messages,
        }
        if tools:
            body["tools"] = tools

        response = await self._client.post("/chat/completions", json=body)

        if response.status_code == 429:
            self._rate_limiter.record_actual(0, estimated)
            raise _classify_429(response)

        response.raise_for_status()
        payload = response.json()

        usage = payload.get("usage") or {}
        actual_total = int(usage.get("total_tokens", estimated))
        self._rate_limiter.record_actual(actual_total, estimated)

        try:
            return payload["choices"][0]["message"]
        except (KeyError, IndexError) as e:
            logger.error("[OpenAI] 응답 파싱 실패 - %s", e)
            raise

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
        match = re.search(r'```(?:json)?\s*(.*?)```', content, re.DOTALL)
        if match:
            content = match.group(1)
        try:
            parsed = json.loads(content.strip())
        except (json.JSONDecodeError, ValueError) as e:
            logger.error("[OpenAI] 응답 파싱 실패 - %s", e)
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
