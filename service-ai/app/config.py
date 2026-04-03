from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # AI API
    ai_provider: str = "stub"  # "stub" | "openai"
    openai_api_key: str = ""
    openai_model: str = "gpt-4o-mini"

    # RabbitMQ
    rabbitmq_url: str = "amqp://guest:guest@localhost:5672/"
    rabbitmq_queue: str = "ai.congestion.analysis"

    # Batch
    batch_window_seconds: float = 2.0
    batch_max_size: int = 10

    # Redis
    redis_url: str = "redis://localhost:6379/0"
    redis_report_ttl_hours: int = 4

    # MySQL
    mysql_url: str = "mysql+aiomysql://root:root@localhost:3306/danburn_ai"

    model_config = {"env_prefix": "", "env_file": ".env"}


settings = Settings()
