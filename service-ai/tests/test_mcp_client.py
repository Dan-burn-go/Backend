"""app.ai.mcp.client — MCPClient 상태 및 예외 경로 테스트.

실제 MCP 서버 연결(create_connected_server_and_client_session)은 mock으로 대체한다.
"""

from __future__ import annotations

from contextlib import asynccontextmanager
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.ai.mcp.client import MCPClient


def _make_fake_tool(name: str, description: str = "desc") -> MagicMock:
    tool = MagicMock()
    tool.name = name
    tool.description = description
    tool.inputSchema = {"type": "object", "properties": {}}
    return tool


class TestMCPClientNotStarted:
    @pytest.mark.asyncio
    async def test_list_tools_raises_when_not_started(self):
        client = MCPClient()
        with pytest.raises(RuntimeError, match="시작되지 않았습니다"):
            await client.list_tools()

    @pytest.mark.asyncio
    async def test_call_tool_raises_when_not_started(self):
        client = MCPClient()
        with pytest.raises(RuntimeError, match="시작되지 않았습니다"):
            await client.call_tool("search_web", {})

    @pytest.mark.asyncio
    async def test_stop_safe_when_not_started(self):
        client = MCPClient()
        await client.stop()  # should not raise


class TestMCPClientStarted:
    def _fake_session(self, tools=None, call_result_text="ok"):
        """list_tools / call_tool 을 흉내내는 가짜 ClientSession."""
        session = AsyncMock()
        tool_list = MagicMock()
        tool_list.tools = tools or []
        session.list_tools.return_value = tool_list

        call_result = MagicMock()
        content_item = MagicMock()
        content_item.text = call_result_text
        call_result.content = [content_item]
        session.call_tool.return_value = call_result
        return session

    @asynccontextmanager
    async def _patched_start(self, client: MCPClient, session):
        """create_connected_server_and_client_session 을 patch하여 start() 실행."""
        fake_ctx = AsyncMock()
        fake_ctx.__aenter__ = AsyncMock(return_value=session)
        fake_ctx.__aexit__ = AsyncMock(return_value=False)

        with patch(
            "app.ai.mcp.client.create_connected_server_and_client_session",
            return_value=fake_ctx,
        ):
            await client.start()
            yield

    @pytest.mark.asyncio
    async def test_start_idempotent(self):
        """이미 시작된 클라이언트에 start() 재호출 → 예외 없음."""
        client = MCPClient()
        session = self._fake_session()
        async with self._patched_start(client, session):
            await client.start()  # 두 번째 호출 → early return
            assert client._session is session  # 세션 변경 없음

    @pytest.mark.asyncio
    async def test_list_tools_returns_openai_format(self):
        client = MCPClient()
        tools = [_make_fake_tool("search_web"), _make_fake_tool("get_weather")]
        session = self._fake_session(tools=tools)
        async with self._patched_start(client, session):
            result = await client.list_tools()
        assert len(result) == 2
        assert result[0]["type"] == "function"
        assert result[0]["function"]["name"] == "search_web"
        assert result[1]["function"]["name"] == "get_weather"

    @pytest.mark.asyncio
    async def test_call_tool_returns_text_content(self):
        client = MCPClient()
        session = self._fake_session(call_result_text="검색 결과입니다")
        async with self._patched_start(client, session):
            result = await client.call_tool("search_web", {"query": "강남 혼잡"})
        assert "검색 결과입니다" in result

    @pytest.mark.asyncio
    async def test_call_tool_empty_content_returns_json_fallback(self):
        client = MCPClient()
        session = AsyncMock()
        tool_list = MagicMock()
        tool_list.tools = []
        session.list_tools.return_value = tool_list
        call_result = MagicMock()
        # content 없음 (text attribute 없는 항목)
        item = MagicMock(spec=[])  # no 'text' attribute
        call_result.content = [item]
        session.call_tool.return_value = call_result

        async with self._patched_start(client, session):
            result = await client.call_tool("search_web", {})
        assert result == '{"results": []}'
