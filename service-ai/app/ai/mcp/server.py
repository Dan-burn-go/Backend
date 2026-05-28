"""In-process MCP 서버 — search_web tool 제공.

- 같은 프로세스 안에서 in-memory transport로 클라이언트 연결
- DuckDuckGo News 검색으로 외부 이벤트(축제, 콘서트, 시위 등) 정보 조회
- 결과는 제목 + 기사 발행일 + 본문 snippet (LLM 이 행사 일시 검증 가능)
"""

from __future__ import annotations

import asyncio
import logging
import re
from typing import Any

from ddgs import DDGS
from mcp.server.fastmcp import FastMCP

from app.config import settings

logger = logging.getLogger(__name__)

mcp_server = FastMCP("ai-search-server")

BODY_SNIPPET_MAX_CHARS = 200
SEARCH_MAX_ATTEMPTS = 2
SEARCH_RETRY_BACKOFF_SECONDS = 0.5

# DDG News 결과를 0건으로 만드는 날짜 토큰 패턴
# ISO/슬래시/점 구분자 전체 우선 매칭 → 짧은 형태 → 한국식 단위 순서로 처리
# 연도는 전후 숫자가 없는 19xx/20xx 4자리만 매칭 (인원수 '1200명' 등 오매칭 방지)
_DATE_TOKEN_RE = re.compile(
    r"\d{4}[-./]\d{1,2}[-./]\d{1,2}|\d{1,2}[-./]\d{1,2}|(?<!\d)(?:19|20)\d{2}(?!\d)(?:\s*년|(?=\b|$))|\d+\s*월|\d+\s*일"
)


def _sanitize_query(query: str) -> str:
    """날짜 토큰 제거 후 공백 정리. 결과가 빈 문자열이면 원본 반환."""
    sanitized = _DATE_TOKEN_RE.sub("", query)
    sanitized = re.sub(r" +", " ", sanitized).strip()
    return sanitized if sanitized else query


@mcp_server.tool()
async def search_web(query: str) -> dict[str, Any]:
    """외부 이벤트(축제, 콘서트, 시위 등) 정보를 DuckDuckGo News에서 검색.

    Args:
        query: 한국어 검색 쿼리 (예: "강남역 5월 행사")

    Returns:
        성공: {"results": [{"title": ..., "date": ..., "body": ...}, ...]}
        실패: {"error": "search_timeout" | "search_failed", "results": []}

    date 는 기사 발행일이며 행사일과 다름. body 는 행사 일시·장소 검증용 snippet.
    DDG의 일시 응답 파싱 오류(DecodeError) 대비로 1회 재시도한다.
    최종 실패해도 빈 결과로 반환하여 분석은 계속 진행되도록 한다 (WARN).
    """
    sanitized = _sanitize_query(query)
    if sanitized != query:
        logger.info(
            "[MCP] search_web query strip - before=%s after=%s",
            query, sanitized,
        )
    query = sanitized

    raw: list[dict[str, Any]] | None = None
    last_error: BaseException | None = None
    for attempt in range(1, SEARCH_MAX_ATTEMPTS + 1):
        try:
            raw = await asyncio.wait_for(
                asyncio.to_thread(_ddg_news, query),
                timeout=settings.mcp_tool_timeout_seconds,
            )
            last_error = None
            break
        except asyncio.TimeoutError as e:
            last_error = e
            if attempt < SEARCH_MAX_ATTEMPTS:
                logger.warning(
                    "[MCP] search_web 타임아웃, 재시도 - query=%s, attempt=%d",
                    query, attempt,
                )
                await asyncio.sleep(SEARCH_RETRY_BACKOFF_SECONDS)
        except Exception as e:
            last_error = e
            if attempt < SEARCH_MAX_ATTEMPTS:
                logger.warning(
                    "[MCP] search_web 일시 실패, 재시도 - query=%s, attempt=%d, error=%s",
                    query, attempt, type(e).__name__,
                )
                await asyncio.sleep(SEARCH_RETRY_BACKOFF_SECONDS)

    if last_error is not None:
        error_kind = (
            "search_timeout" if isinstance(last_error, asyncio.TimeoutError)
            else "search_failed"
        )
        # 재시도 후 최종 실패 — 분석은 빈 결과로 진행되도록 WARN
        logger.warning(
            "[MCP] search_web 최종 실패 - query=%s, kind=%s, error=%s",
            query, error_kind, type(last_error).__name__,
        )
        return {"error": error_kind, "results": []}

    results = [
        {
            "title": r.get("title", ""),
            "date": r.get("date", ""),
            "body": _truncate(r.get("body", "")),
        }
        for r in (raw or [])[: settings.mcp_search_max_results]
    ]
    return {"results": results}


def _truncate(text: str) -> str:
    if not text:
        return ""
    if len(text) <= BODY_SNIPPET_MAX_CHARS:
        return text
    return text[:BODY_SNIPPET_MAX_CHARS].rstrip() + "…"


def _ddg_news(query: str) -> list[dict[str, Any]]:
    """DDG News 동기 호출 (asyncio.to_thread로 wrap)."""
    with DDGS() as ddgs:
        return ddgs.news(
            query=query,
            region=settings.mcp_search_region,
            max_results=settings.mcp_search_max_results,
        ) or []
