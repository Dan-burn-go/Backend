from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # AI API
    ai_provider: str = "stub"  # "stub" | "openai"
    openai_base_url: str = "https://api.cerebras.ai/v1"
    openai_api_key: str = ""
    openai_model: str = "qwen-3-235b-a22b-instruct-2507"

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
