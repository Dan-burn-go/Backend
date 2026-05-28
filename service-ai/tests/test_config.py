"""app.config — Settings 기본값 및 환경변수 주입 테스트."""

from __future__ import annotations

import os

import pytest

from app.config import Settings


class TestSettingsDefaults:
    def test_default_ai_provider(self):
        s = Settings(rabbitmq_url="amqp://localhost/")
        assert s.ai_provider == "stub"

    def test_default_rabbitmq_queue(self):
        s = Settings(rabbitmq_url="amqp://localhost/")
        assert s.rabbitmq_queue == "ai.congestion.analysis"

    def test_default_dlq_name(self):
        s = Settings(rabbitmq_url="amqp://localhost/")
        assert s.rabbitmq_dlq_name == "ai.congestion.dlq"

    def test_default_dlx_name(self):
        s = Settings(rabbitmq_url="amqp://localhost/")
        assert s.dlq_dlx_name == "ai.congestion.dlx"

    def test_default_batch_window(self):
        s = Settings(rabbitmq_url="amqp://localhost/")
        assert s.batch_window_seconds == pytest.approx(5.0)

    def test_default_rpm_limit(self):
        s = Settings(rabbitmq_url="amqp://localhost/")
        assert s.rpm_limit == 4

    def test_rabbitmq_url_required(self):
        """rabbitmq_url 없이 Settings() → ValidationError."""
        from pydantic import ValidationError

        # 환경변수가 없는 격리 상태로 확인
        env_backup = os.environ.pop("RABBITMQ_URL", None)
        try:
            with pytest.raises((ValidationError, Exception)):
                Settings()
        finally:
            if env_backup is not None:
                os.environ["RABBITMQ_URL"] = env_backup

    def test_override_via_constructor(self):
        s = Settings(rabbitmq_url="amqp://localhost/", ai_provider="openai", rpm_limit=2)
        assert s.ai_provider == "openai"
        assert s.rpm_limit == 2

    def test_dlq_message_ttl_ms_default(self):
        s = Settings(rabbitmq_url="amqp://localhost/")
        assert s.dlq_message_ttl_ms == 86_400_000

    def test_dlq_max_length_default(self):
        s = Settings(rabbitmq_url="amqp://localhost/")
        assert s.dlq_max_length == 10_000
