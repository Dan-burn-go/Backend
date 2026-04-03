import asyncio
import json
import logging

import aio_pika
from aio_pika.abc import AbstractIncomingMessage

from app.rabbitmq.batch import BatchProcessor
from app.config import settings
from app.models.schemas import CongestionEvent

logger = logging.getLogger(__name__)

MAX_RECONNECT_DELAY = 30


class RabbitMQConsumer:
    """RabbitMQ에서 혼잡도 이벤트를 수신하여 BatchProcessor에 전달한다."""

    def __init__(self, batch_processor: BatchProcessor) -> None:
        self._batch = batch_processor
        self._connection: aio_pika.abc.AbstractRobustConnection | None = None
        self._channel: aio_pika.abc.AbstractChannel | None = None
        self._running = False

    async def start(self) -> None:
        self._running = True
        await self._connect()

    async def stop(self) -> None:
        self._running = False
        if self._channel:
            await self._channel.close()
        if self._connection:
            await self._connection.close()
        logger.info("[Consumer] 종료 완료")

    async def _connect(self) -> None:
        delay = 1
        while self._running:
            try:
                self._connection = await aio_pika.connect_robust(settings.rabbitmq_url)
                self._channel = await self._connection.channel()
                await self._channel.set_qos(prefetch_count=10)

                queue = await self._channel.declare_queue(
                    settings.rabbitmq_queue, durable=True
                )
                await queue.consume(self._on_message)

                logger.info("[Consumer] RabbitMQ 연결 완료 - queue=%s", settings.rabbitmq_queue)
                return
            except Exception as e:
                logger.warning("[Consumer] 연결 실패, %d초 후 재시도 - %s", delay, e)
                await asyncio.sleep(delay)
                delay = min(delay * 2, MAX_RECONNECT_DELAY)

    async def _on_message(self, message: AbstractIncomingMessage) -> None:
        try:
            body = json.loads(message.body.decode())
            event = CongestionEvent(
                area_code=body["areaCode"],
                congestion_level=body["congestionLevel"],
                max_people_count=body["maxPeopleCount"],
                population_time=body["populationTime"],
            )
            await self._batch.add(event)
            await message.ack()
        except Exception as e:
            logger.error("[Consumer] 메시지 처리 실패, 폐기 - %s", e)
            await message.nack(requeue=False)
