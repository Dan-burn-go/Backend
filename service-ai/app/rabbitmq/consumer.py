import asyncio
import json
import logging

import aio_pika
from aio_pika.abc import AbstractIncomingMessage
from aio_pika.exceptions import ChannelPreconditionFailed

from app.config import settings
from app.models.schemas import CongestionEvent
from app.rabbitmq.batch import BatchProcessor

logger = logging.getLogger(__name__)

MAX_RECONNECT_DELAY = 30


class RabbitMQConsumer:
    """RabbitMQ 혼잡도 이벤트 수신 → BatchProcessor 전달.

    - DLX/DLQ 토폴로지 선언
    - 메인 큐: x-dead-letter-exchange 인자 포함
    - ack/nack: BatchProcessor 담당 (여기서는 파싱 실패만 즉시 DLQ)
    """

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

    async def _declare_topology(self, channel: aio_pika.abc.AbstractChannel) -> aio_pika.abc.AbstractQueue:
        """DLX, DLQ, 메인 큐 선언.

        - 1) DLX (direct, durable)
        - 2) DLQ (TTL + max-length) → DLX 에 바인딩
        - 3) 메인 큐 (x-dead-letter-exchange 인자 포함)
        - 기존 메인 큐가 DLX 인자 없이 존재 → PRECONDITION_FAILED 발생
          · 대응: 경고 로그 + passive 재선언 (마이그레이션 안내)
          · 마이그레이션: 관리 UI 에서 큐 삭제 또는 재선언 스크립트 필요
        """
        # 1. DLX 선언
        dlx = await channel.declare_exchange(
            settings.dlq_dlx_name,
            aio_pika.ExchangeType.DIRECT,
            durable=True,
        )

        # 2. DLQ 선언 (TTL + max-length)
        dlq = await channel.declare_queue(
            settings.rabbitmq_dlq_name,
            durable=True,
            arguments={
                "x-message-ttl": settings.dlq_message_ttl_ms,
                "x-max-length": settings.dlq_max_length,
            },
        )
        await dlq.bind(dlx, routing_key=settings.rabbitmq_queue)

        # 3. 메인 큐 선언 (DLX 인자 포함)
        main_args = {
            "x-dead-letter-exchange": settings.dlq_dlx_name,
            "x-dead-letter-routing-key": settings.rabbitmq_queue,
        }
        try:
            queue = await channel.declare_queue(
                settings.rabbitmq_queue,
                durable=True,
                arguments=main_args,
            )
        except ChannelPreconditionFailed as e:
            logger.warning(
                "[Consumer] 메인 큐 '%s' 가 DLX 인자 없이 이미 존재합니다. "
                "DLQ 라우팅 활성화 → RabbitMQ Management UI 에서 큐 삭제 후 재시작 필요. 원인: %s",
                settings.rabbitmq_queue,
                e,
            )
            # PRECONDITION_FAILED → 채널 닫힘 가능성 → 새 채널 확보
            try:
                self._channel = await self._connection.channel()  # type: ignore[union-attr]
                await self._channel.set_qos(prefetch_count=5)
                queue = await self._channel.declare_queue(
                    settings.rabbitmq_queue,
                    durable=True,
                )
            except Exception:
                raise
        return queue

    async def _connect(self) -> None:
        delay = 1
        while self._running:
            try:
                self._connection = await aio_pika.connect_robust(settings.rabbitmq_url)
                self._channel = await self._connection.channel()
                await self._channel.set_qos(prefetch_count=5)

                queue = await self._declare_topology(self._channel)
                await queue.consume(self._on_message)

                logger.info(
                    "[Consumer] RabbitMQ 연결 완료 - queue=%s, dlq=%s, dlx=%s",
                    settings.rabbitmq_queue,
                    settings.rabbitmq_dlq_name,
                    settings.dlq_dlx_name,
                )
                return
            except Exception as e:
                logger.warning("[Consumer] 연결 실패, %d초 후 재시도 - %s", delay, e)
                await asyncio.sleep(delay)
                delay = min(delay * 2, MAX_RECONNECT_DELAY)

    async def _on_message(self, message: AbstractIncomingMessage) -> None:
        """메시지 파싱 → BatchProcessor 전달.

        - ack/nack 은 BatchProcessor 담당 (여기서 호출 금지)
        - 파싱 실패만 즉시 nack(requeue=False) → DLQ
        """
        try:
            body = json.loads(message.body.decode())
            event = CongestionEvent(
                area_name=body["areaName"],
                area_code=body["areaCode"],
                congestion_level=body["congestionLevel"],
                max_people_count=body["maxPeopleCount"],
                population_time=body["populationTime"],
            )
        except Exception as e:
            logger.error("[Consumer] 메시지 파싱 실패, DLQ 라우팅 - %s", e)
            try:
                await message.nack(requeue=False)
            except Exception as nack_err:  # pragma: no cover - 방어적 로깅
                logger.error("[Consumer] nack 실패 - %s", nack_err)
            return

        await self._batch.add(event, message)
