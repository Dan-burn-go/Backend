import json
import logging

import aio_pika

from app.config import settings
from app.models.schemas import AnalysisResult

logger = logging.getLogger(__name__)

EXCHANGE_NAME = "congestion.events"
REPORT_ROUTING_KEY = "ai.report"


class RabbitMQPublisher:
    """AI 분석 결과를 RabbitMQ로 발행한다."""

    def __init__(self) -> None:
        self._connection: aio_pika.abc.AbstractRobustConnection | None = None
        self._channel: aio_pika.abc.AbstractChannel | None = None
        self._exchange: aio_pika.abc.AbstractExchange | None = None

    async def connect(self) -> None:
        self._connection = await aio_pika.connect_robust(settings.rabbitmq_url)
        self._channel = await self._connection.channel()
        self._exchange = await self._channel.declare_exchange(
            EXCHANGE_NAME, aio_pika.ExchangeType.TOPIC, durable=True
        )
        logger.info("[Publisher] RabbitMQ 연결 완료 - exchange=%s", EXCHANGE_NAME)

    async def close(self) -> None:
        if self._channel:
            await self._channel.close()
        if self._connection:
            await self._connection.close()
        logger.info("[Publisher] 종료 완료")

    async def publish_all(self, results: list[AnalysisResult]) -> None:
        if not results:
            return
        for r in results:
            body = json.dumps({
                "areaName": r.area_name,
                "areaCode": r.area_code,
                "congestionLevel": r.congestion_level,
                "analysisMessage": r.analysis_message,
                "populationTime": r.population_time,
            }).encode()
            message = aio_pika.Message(
                body=body,
                content_type="application/json",
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
            )
            await self._exchange.publish(message, routing_key=REPORT_ROUTING_KEY)
        logger.info("[Publisher] AI 분석 결과 발행 - %d건", len(results))
