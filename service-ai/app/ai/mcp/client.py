"""in-memory transport로 MCP 서버에 연결된 클라이언트 세션.

- lifespan에서 start/stop 호출
- OpenAIAnalyzer가 list_tools / call_tool 호출
"""

from __future__ import annotations

import json
import logging
from contextlib import AsyncExitStack
from typing import Any

from mcp.client.session import ClientSession
from mcp.shared.memory import create_connected_server_and_client_session

from app.ai.mcp.server import mcp_server

logger = logging.getLogger(__name__)


class MCPClient:
    """In-process MCP 클라이언트 (lifespan 관리)."""

    def __init__(self) -> None:
        self._stack: AsyncExitStack | None = None
        self._session: ClientSession | None = None

    async def start(self) -> None:
        if self._session is not None:
            return
        self._stack = AsyncExitStack()
        await self._stack.__aenter__()
        self._session = await self._stack.enter_async_context(
            create_connected_server_and_client_session(mcp_server)
        )
        logger.info("[MCP] in-process 클라이언트 시작")

    async def stop(self) -> None:
        if self._stack is None:
            return
        await self._stack.aclose()
        self._stack = None
        self._session = None
        logger.info("[MCP] in-process 클라이언트 종료")

    async def list_tools(self) -> list[dict[str, Any]]:
        """OpenAI tool calling 형식의 tool 정의 반환."""
        if self._session is None:
            raise RuntimeError("MCP 클라이언트가 시작되지 않았습니다")
        result = await self._session.list_tools()
        return [
            {
                "type": "function",
                "function": {
                    "name": t.name,
                    "description": t.description or "",
                    "parameters": t.inputSchema or {"type": "object", "properties": {}},
                },
            }
            for t in result.tools
        ]

    async def call_tool(self, name: str, arguments: dict[str, Any]) -> str:
        """tool 실행 → LLM에 줄 문자열 반환."""
        if self._session is None:
            raise RuntimeError("MCP 클라이언트가 시작되지 않았습니다")
        result = await self._session.call_tool(name, arguments)
        parts: list[str] = []
        for content in result.content:
            text = getattr(content, "text", None)
            if text:
                parts.append(text)
        return "\n".join(parts) if parts else json.dumps({"results": []})
