"""OpenAIAnalyzer tool calling 루프 + soft limit + 구조화 로그 테스트.

- httpx.AsyncClient.post 를 AsyncMock 으로 교체해 LLM 응답 시뮬레이션
- MCP 클라이언트는 FakeMCPClient 로 list_tools / call_tool 모킹
- 외부 네트워크 / MCP 서버 미사용
"""

from __future__ import annotations

import json
import logging
from datetime import date
from typing import Any
from unittest.mock import AsyncMock

import httpx
import pytest

from app.ai.openai import OpenAIAnalyzer
from app.models.schemas import CongestionEvent


@pytest.fixture(autouse=True)
def _freeze_today(monkeypatch):
    """이 모듈의 모든 테스트에서 KST '오늘'을 이벤트의 populationTime(5/5) 으로 고정.

    환각 검증이 tool_results 본문에서 '오늘 날짜 마커'를 찾기 때문에,
    테스트 시점의 실제 오늘과 무관하게 결정적이도록 함.
    """
    monkeypatch.setattr(
        "app.ai.openai.client._now_kst_date",
        lambda: date(2026, 5, 5),
    )


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
        "results": [{
            "title": "강남역 콘서트",
            "date": "2026-05-05",
            "body": "5월 5일 오후 8시 강남역 부근 콘서트 개최 예정",
        }]
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


async def test_regenerates_general_analysis_when_no_today_marker(analyzer):
    """검색 결과에 오늘(5/5) 날짜가 없으면 일반 분석으로 재생성한다.

    운영 사고 가드: hop 2 에서 모델이 과거 기사로 사건을 합성해도, tool 미사용
    일반 분석으로 한 번 더 호출(3번째 LLM 콜)해 일반 패턴 원인을 받아 사용.
    '미확인 / 원인 불명' 같은 고정 문구가 아니라 모델이 생성한 응답을 쓴다.
    """
    a, mcp = analyzer
    # 검색 결과는 과거(3월) 기사뿐 → 오늘 날짜 마커 없음
    mcp.call_tool.return_value = json.dumps({
        "results": [{
            "title": "지난 3월 강남역 행사",
            "date": "2026-03-10",
            "body": "3월 10일 강남역 인근 행사 성황",
        }]
    })
    a._client.post.side_effect = [
        _httpx_response(_llm_payload(content=None, tool_calls=[{
            "id": "call_1",
            "type": "function",
            "function": {
                "name": "search_web",
                "arguments": json.dumps({"query": "강남역 행사"}),
            },
        }])),
        # hop 2: 모델이 과거 기사로 사건 합성 시도 (폐기 대상)
        _httpx_response(_final_results_payload(message="3월 행사로 인한 혼잡")),
        # 재생성: tool 미사용 일반 분석
        _httpx_response(_final_results_payload(message="퇴근 시간대 일반 통근 패턴")),
    ]

    results = await a.analyze([_make_event()], mode="anomaly")

    assert len(results) == 1
    assert results[0].analysis_message == "퇴근 시간대 일반 통근 패턴"
    assert "미확인" not in results[0].analysis_message
    assert "원인 불명" not in results[0].analysis_message
    assert a._client.post.await_count == 3  # hop1 + hop2 + 재생성

    # 재생성 호출은 tools 미주입
    regen_body = a._client.post.call_args_list[2].kwargs["json"]
    assert "tools" not in regen_body


async def test_keeps_answer_when_today_marker_present(analyzer):
    """검색 결과에 오늘 날짜가 있으면 재생성하지 않고 모델 응답을 그대로 사용."""
    a, mcp = analyzer
    mcp.call_tool.return_value = json.dumps({
        "results": [{
            "title": "강남역 콘서트",
            "date": "2026-05-05",
            "body": "5월 5일 오후 8시 강남역 부근 콘서트 개최 예정",
        }]
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
        _httpx_response(_final_results_payload(message="가능성: 강남역 인근 콘서트")),
    ]

    results = await a.analyze([_make_event()], mode="anomaly")

    assert results[0].analysis_message == "가능성: 강남역 인근 콘서트"
    assert a._client.post.await_count == 2  # 재생성 없음


async def test_parallel_tool_calls(analyzer):
    a, mcp = analyzer
    mcp.call_tool.return_value = json.dumps({
        "results": [{
            "title": "오늘 행사",
            "date": "2026-05-05",
            "body": "5월 5일 행사 진행",
        }]
    })
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
        "results": [{
            "title": "잠실 콘서트",
            "date": "2026-05-05",
            "body": "2026년 5월 5일 잠실실내체육관에서 콘서트 개최 예정",
        }]
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
    assert payload["tool_results"] == [{
        "title": "잠실 콘서트",
        "date": "2026-05-05",
        "body": "2026년 5월 5일 잠실실내체육관에서 콘서트 개최 예정",
    }]


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
