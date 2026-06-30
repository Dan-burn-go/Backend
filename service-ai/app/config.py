from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # ── AI API ──
    ai_provider: str = "stub"  # "stub" | "openai"
    openai_base_url: str = "https://api.cerebras.ai/v1"
    openai_api_key: str = ""
    openai_model: str = "gpt-oss-120b"

    # ── MCP Tool Calling ──
    mcp_max_tool_hops: int = 1
    mcp_tool_timeout_seconds: float = 5.0
    mcp_search_max_results: int = 5
    mcp_search_region: str = "kr-kr"

    # ── Rate Limit (요청수 + 토큰 예산 기반) ──
    # - gpt-oss-120b: RPM 5 / TPM 30,000 / TPH 1,000,000 / TPD 1,000,000
    # - 운영 여유 마진 적용 (RPM 은 재시도 요청까지 포함 → 1건 여유)
    # - TPH 별도 버킷 미운용: 총량(1M)이 TPD 와 동일 → TPD 버킷이 그대로 커버
    rpm_limit: int = 4
    tpm_limit: int = 25_000
    tpd_limit: int = 900_000
    tpd_soft_limit_ratio: float = 0.8

    # ── LLM 429 재시도 (지수 백오프) ──
    # - 최초 호출 포함 총 시도 횟수. 소진 시 RetriableError 전파 → DLQ
    # - 대기 = max(base * 2**(n-1), retry_after), 상한 max_delay
    # - RPM 슬롯(15초=60/RPM4) 정렬: 15 → 30 → 60초 (인라인 총 ≤105초), 상한 60초=한 분 윈도우
    llm_retry_max_attempts: int = 4
    llm_retry_base_delay: float = 15.0
    llm_retry_max_delay: float = 60.0

    # ── RabbitMQ ──
    rabbitmq_url: str  # amqp://{user}:{pass}@{host}:{port}/
    rabbitmq_queue: str = "ai.congestion.analysis"
    rabbitmq_anomaly_queue: str = "ai.congestion.anomaly"

    # ── RabbitMQ DLX / DLQ ──
    # - DLX: direct, durable
    # - DLQ: TTL 24h, max-length 10,000
    # - 재처리 워커: 10분 주기, 사이클당 최대 5건 (RPM 5 한도 내 — 라이브 트래픽과 공유)
    dlq_dlx_name: str = "ai.congestion.dlx"
    rabbitmq_dlq_name: str = "ai.congestion.dlq"
    dlq_message_ttl_ms: int = 86_400_000  # 24h
    dlq_max_length: int = 10_000
    dlq_reprocess_interval_seconds: int = 600  # 10분
    dlq_reprocess_batch_max: int = 5
    message_max_attempt: int = 3

    # ── Batch ──
    batch_window_seconds: float = 5.0
    batch_max_size: int = 3

    # ── Observability ──
    otlp_traces_url: str = "http://localhost:4318/v1/traces"
    loki_url: str = "http://localhost:3100/loki/api/v1/push"

    model_config = {"env_prefix": "", "env_file": ".env"}


settings = Settings()
