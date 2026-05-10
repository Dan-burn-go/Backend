"""OpenAIAnalyzer tool calling 루프 + soft limit + 구조화 로그 테스트.

- httpx.AsyncClient.post 를 AsyncMock 으로 교체해 LLM 응답 시뮬레이션
- MCP 클라이언트는 FakeMCPClient 로 list_tools / call_tool 모킹
- 외부 네트워크 / MCP 서버 미사용
"""

from __future__ import annotations

import json
import logging
from typing import Any
from unittest.mock import AsyncMock

import httpx
import pytest

from app.ai.openai import OpenAIAnalyzer
from app.models.schemas import CongestionEvent


def _make_event(
    area_code: str = "POI001",
    area_name: str = "강남역",
) -> CongestionEvent:
    return CongestionEvent(
        area_code=area_code,
        area_name=area_name,
        congestion_level="BUSY",
        max_people_count=50000,
        population_time="2026-05-05 14:00",
    )


def _httpx_response(payload: dict[str, Any]) -> httpx.Response:
    return httpx.Response(
        status_code=200,
        content=json.dumps(payload).encode(),
        headers={"content-type": "application/json"},
        request=httpx.Request("POST", "https://test/chat/completions"),
    )


def _llm_payload(
    content: str | None = None,
    tool_calls: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    msg: dict[str, Any] = {"role": "assistant", "content": content}
    if tool_calls is not None:
        msg["tool_calls"] = tool_calls
    return {"choices": [{"message": msg}], "usage": {"total_tokens": 100}}


def _final_results_payload(area_code: str = "POI001", message: str = "분석 결과") -> dict[str, Any]:
    return _llm_payload(content=json.dumps({"results": [{
        "area_code": area_code,
        "area_name": "강남역",
        "analysis_message": message,
    }]}))


class FakeMCPClient:
    def __init__(self) -> None:
        self.list_tools = AsyncMock(return_value=[
            {
                "type": "function",
                "function": {
                    "name": "search_web",
                    "description": "외부 이벤트 검색",
                    "parameters": {
                        "type": "object",
                        "properties": {"query": {"type": "string"}},
                        "required": ["query"],
                    },
                },
            }
        ])
        self.call_tool = AsyncMock()


@pytest.fixture
def analyzer():
    mcp = FakeMCPClient()
    a = OpenAIAnalyzer(mcp)
    a._client.post = AsyncMock()
    return a, mcp


async def test_no_tool_calls_direct_answer(analyzer):
    a, mcp = analyzer
    a._client.post.return_value = _httpx_response(_final_results_payload(
        message="평일 점심 일반 패턴"
    ))

    results = await a.analyze([_make_event()])

    assert len(results) == 1
    assert results[0].analysis_message == "평일 점심 일반 패턴"
    assert a._client.post.await_count == 1  # hop 1만
    mcp.call_tool.assert_not_awaited()


async def test_single_tool_call_then_final_answer(analyzer):
    a, mcp = analyzer
    mcp.call_tool.return_value = json.dumps({
        "results": [{"title": "강남역 콘서트", "date": "2026-05-05"}]
    })
    a._client.post.side_effect = [
        _httpx_response(_llm_payload(content=None, tool_calls=[{
            "id": "call_1",
            "type": "function",
            "function": {
                "name": "search_web",
                "arguments": json.dumps({"query": "강남역 콘서트"}),
            },
        }])),
        _httpx_response(_final_results_payload(message="콘서트로 인한 혼잡")),
    ]

    results = await a.analyze([_make_event()])

    assert len(results) == 1
    assert "콘서트" in results[0].analysis_message
    assert a._client.post.await_count == 2  # hop 1 + hop 2
    mcp.call_tool.assert_awaited_once_with("search_web", {"query": "강남역 콘서트"})

    # hop 2 호출 본문에 tools 미주입 (가드레일 #1)
    second_call_body = a._client.post.call_args_list[1].kwargs["json"]
    assert "tools" not in second_call_body


async def test_parallel_tool_calls(analyzer):
    a, mcp = analyzer
    mcp.call_tool.return_value = json.dumps({"results": []})
    a._client.post.side_effect = [
        _httpx_response(_llm_payload(content=None, tool_calls=[
            {
                "id": "call_1",
                "type": "function",
                "function": {
                    "name": "search_web",
                    "arguments": json.dumps({"query": "강남역 행사"}),
                },
            },
            {
                "id": "call_2",
                "type": "function",
                "function": {
                    "name": "search_web",
                    "arguments": json.dumps({"query": "잠실 콘서트"}),
                },
            },
        ])),
        _httpx_response(_llm_payload(content=json.dumps({"results": [
            {"area_code": "POI001", "area_name": "강남역", "analysis_message": "행사"},
            {"area_code": "POI002", "area_name": "잠실", "analysis_message": "콘서트"},
        ]}))),
    ]

    events = [_make_event("POI001", "강남역"), _make_event("POI002", "잠실")]
    results = await a.analyze(events)

    assert len(results) == 2
    assert mcp.call_tool.await_count == 2
    queries = {c.args[1]["query"] for c in mcp.call_tool.call_args_list}
    assert queries == {"강남역 행사", "잠실 콘서트"}


async def test_soft_limit_disables_tools(analyzer, monkeypatch):
    a, mcp = analyzer
    # tpd 사용률 90% → soft_limit_ratio(0.8) 초과 → tools 미주입
    monkeypatch.setattr(a._rate_limiter, "tpd_used_ratio", lambda: 0.9)
    a._client.post.return_value = _httpx_response(_final_results_payload(
        message="기본 분석"
    ))

    results = await a.analyze([_make_event()])

    assert len(results) == 1
    mcp.list_tools.assert_not_awaited()
    mcp.call_tool.assert_not_awaited()
    # 호출 본문에 tools 필드 없음
    sent_body = a._client.post.call_args.kwargs["json"]
    assert "tools" not in sent_body


async def test_structured_log_tool_called_true(analyzer, caplog):
    a, mcp = analyzer
    mcp.call_tool.return_value = json.dumps({
        "results": [{"title": "잠실 콘서트", "date": "2026-05-05"}]
    })
    a._client.post.side_effect = [
        _httpx_response(_llm_payload(content=None, tool_calls=[{
            "id": "call_1",
            "type": "function",
            "function": {
                "name": "search_web",
                "arguments": json.dumps({"query": "잠실 콘서트"}),
            },
        }])),
        _httpx_response(_final_results_payload(message="콘서트")),
    ]

    with caplog.at_level(logging.INFO, logger="app.ai.openai.client"):
        await a.analyze([_make_event()])

    log = next(
        (r for r in caplog.records if r.getMessage().startswith("[Analysis] ")),
        None,
    )
    assert log is not None, "구조화 로그 없음"
    payload = json.loads(log.getMessage().split("[Analysis] ", 1)[1])
    assert payload["tool_called"] is True
    assert payload["tool_queries"] == ["잠실 콘서트"]
    assert payload["tool_results"] == [{"title": "잠실 콘서트", "date": "2026-05-05"}]


async def test_structured_log_tool_called_false(analyzer, caplog):
    a, mcp = analyzer
    a._client.post.return_value = _httpx_response(_final_results_payload(
        message="일반 패턴"
    ))

    with caplog.at_level(logging.INFO, logger="app.ai.openai.client"):
        await a.analyze([_make_event()])

    log = next(
        (r for r in caplog.records if r.getMessage().startswith("[Analysis] ")),
        None,
    )
    assert log is not None
    payload = json.loads(log.getMessage().split("[Analysis] ", 1)[1])
    assert payload["tool_called"] is False
    assert "tool_queries" not in payload
    assert "tool_results" not in payload
