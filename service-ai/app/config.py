from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # AI API
    ai_provider: str = "stub"  # "stub" | "openai"
    openai_base_url: str = "https://api.openai.com/v1"  # 오라클 자체 호스팅 시 Cloudflare Tunnel URL로 변경 (Infisical)
    openai_api_key: str = ""  # 오라클 AI 서버 접근 시 해당 서버의 API Key 필요 (Infisical)
    openai_model: str = "gpt-4o-mini"

    # RabbitMQ
    rabbitmq_url: str  # amqp://{user}:{pass}@{host}:{port}/
    rabbitmq_queue: str = "ai.congestion.analysis"

    # Batch
    batch_window_seconds: float = 2.0
    batch_max_size: int = 10

    # Redis
    redis_url: str  # redis://:{password}@{host}:{port}/{db}
    redis_report_ttl_hours: int = 4

    # MySQL
    mysql_url: str  # mysql+aiomysql://{user}:{pass}@{host}:{port}/{db}

    model_config = {"env_prefix": "", "env_file": ".env"}


settings = Settings()
