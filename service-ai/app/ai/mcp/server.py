"""In-process MCP 서버 — search_web tool 제공.

- 같은 프로세스 안에서 in-memory transport로 클라이언트 연결
- DuckDuckGo News 검색으로 외부 이벤트(축제, 콘서트, 시위 등) 정보 조회
- 결과는 제목 + 날짜만 (토큰 절약)
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any

from ddgs import DDGS
from mcp.server.fastmcp import FastMCP

from app.config import settings

logger = logging.getLogger(__name__)

mcp_server = FastMCP("ai-search-server")


@mcp_server.tool()
async def search_web(query: str) -> dict[str, Any]:
    """외부 이벤트(축제, 콘서트, 시위 등) 정보를 DuckDuckGo News에서 검색.

    Args:
        query: 한국어 검색 쿼리 (예: "강남역 5월 행사")

    Returns:
        성공: {"results": [{"title": ..., "date": ...}, ...]}
        실패: {"error": "search_timeout" | "search_failed", "results": []}
    """
    try:
        raw = await asyncio.wait_for(
            asyncio.to_thread(_ddg_news, query),
            timeout=settings.mcp_tool_timeout_seconds,
        )
    except asyncio.TimeoutError:
        logger.warning("[MCP] search_web 타임아웃 - query=%s", query)
        return {"error": "search_timeout", "results": []}
    except Exception as e:
        logger.error("[MCP] search_web 실패 - query=%s, error=%s", query, e)
        return {"error": "search_failed", "results": []}

    results = [
        {"title": r.get("title", ""), "date": r.get("date", "")}
        for r in (raw or [])[: settings.mcp_search_max_results]
    ]
    return {"results": results}


def _ddg_news(query: str) -> list[dict[str, Any]]:
    """DDG News 동기 호출 (asyncio.to_thread로 wrap)."""
    return DDGS().news(
        query=query,
        region=settings.mcp_search_region,
        max_results=settings.mcp_search_max_results,
    ) or []
