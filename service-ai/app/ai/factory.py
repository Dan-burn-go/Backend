from app.ai.interface import AIAnalyzer
from app.config import settings


def create_analyzer() -> AIAnalyzer:
    """환경 변수 ai_provider에 따라 적절한 구현체를 반환한다."""
    if settings.ai_provider == "openai":
        from app.ai.openai_client import OpenAIAnalyzer
        return OpenAIAnalyzer()

    from app.ai.stub import StubAnalyzer
    return StubAnalyzer()
