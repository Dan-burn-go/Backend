"""app.ai.factory — create_analyzer() 분기 테스트."""

from __future__ import annotations

import pytest

from app.ai.factory import create_analyzer
from app.ai.stub import StubAnalyzer


class TestCreateAnalyzer:
    def test_stub_provider_returns_stub_analyzer(self, monkeypatch):
        monkeypatch.setattr("app.ai.factory.settings.ai_provider", "stub")
        analyzer = create_analyzer()
        assert isinstance(analyzer, StubAnalyzer)

    def test_stub_provider_ignores_mcp_client(self, monkeypatch):
        monkeypatch.setattr("app.ai.factory.settings.ai_provider", "stub")
        # mcp_client 전달해도 StubAnalyzer 반환
        analyzer = create_analyzer(mcp_client=object())  # type: ignore[arg-type]
        assert isinstance(analyzer, StubAnalyzer)

    def test_openai_provider_without_mcp_client_raises(self, monkeypatch):
        monkeypatch.setattr("app.ai.factory.settings.ai_provider", "openai")
        with pytest.raises(RuntimeError, match="mcp_client"):
            create_analyzer(mcp_client=None)

    def test_unknown_provider_falls_back_to_stub(self, monkeypatch):
        """기타 provider 값 → else 분기 → StubAnalyzer."""
        monkeypatch.setattr("app.ai.factory.settings.ai_provider", "unknown")
        analyzer = create_analyzer()
        assert isinstance(analyzer, StubAnalyzer)
