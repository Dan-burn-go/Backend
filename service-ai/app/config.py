from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # ── AI API ──
    ai_provider: str = "stub"  # "stub" | "openai"
    openai_base_url: str = "https://api.cerebras.ai/v1"
    openai_api_key: str = ""
    openai_model: str = "qwen-3-235b-a22b-instruct-2507"

    # ── MCP Tool Calling ──
    mcp_max_tool_hops: int = 1
    mcp_tool_timeout_seconds: float = 5.0
    mcp_search_max_results: int = 5
    mcp_search_region: str = "kr-kr"

    # ── Rate Limit (토큰 예산 기반) ──
    # - Cerebras Free Tier: TPM 60,000 / TPD 1,000,000
    # - 운영 여유 마진 적용
    tpm_limit: int = 50_000
    tpd_limit: int = 900_000
    tpd_soft_limit_ratio: float = 0.8
    # 레거시 호환용 (구 RPM 기반 설정)
    rate_limit_rpm: int = 25

    # ── RabbitMQ ──
    rabbitmq_url: str  # amqp://{user}:{pass}@{host}:{port}/
    rabbitmq_queue: str = "ai.congestion.analysis"

    # ── RabbitMQ DLX / DLQ ──
    # - DLX: direct, durable
    # - DLQ: TTL 24h, max-length 10,000
    # - 재처리 워커: 10분 주기, 사이클당 최대 50건
    dlq_dlx_name: str = "ai.congestion.dlx"
    rabbitmq_dlq_name: str = "ai.congestion.dlq"
    dlq_message_ttl_ms: int = 86_400_000  # 24h
    dlq_max_length: int = 10_000
    dlq_reprocess_interval_seconds: int = 600  # 10분
    dlq_reprocess_batch_max: int = 50
    message_max_attempt: int = 3

    # ── Batch ──
    batch_window_seconds: float = 5.0
    batch_max_size: int = 3

    # ── Observability ──
    otlp_traces_url: str = "http://localhost:4318/v1/traces"
    loki_url: str = "http://localhost:3100/loki/api/v1/push"

    model_config = {"env_prefix": "", "env_file": ".env"}


settings = Settings()
