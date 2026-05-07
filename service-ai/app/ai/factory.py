from app.ai.interface import AIAnalyzer
from app.ai.mcp.client import MCPClient
from app.config import settings


def create_analyzer(mcp_client: MCPClient | None = None) -> AIAnalyzer:
    """환경 변수 ai_provider에 따라 적절한 구현체를 반환한다.

    openai provider 는 MCP tool calling 사용을 위해 mcp_client 필수.
    """
    if settings.ai_provider == "openai":
        if mcp_client is None:
            raise RuntimeError("openai provider 는 mcp_client 가 필요합니다")
        from app.ai.openai import OpenAIAnalyzer
        return OpenAIAnalyzer(mcp_client)

    from app.ai.stub import StubAnalyzer
    return StubAnalyzer()
