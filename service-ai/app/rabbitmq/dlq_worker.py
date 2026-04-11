"""DLQ 재처리 워커.

- 목적: Cerebras 여유 시간대(새벽 등)에 DLQ 자동 소화 → 최종 일관성 보장
- 동작: 일정 주기로 DLQ 메시지를 꺼내 메인 큐로 재발행
- 실패 처리: 재발행 실패 시 DLQ 반환 (requeue=True) → 다음 주기 재시도
"""

from __future__ import annotations

import asyncio
import logging

import aio_pika
from aio_pika.abc import AbstractIncomingMessage

from app.config import settings

logger = logging.getLogger(__name__)


class DLQWorker:
    """DLQ → 메인 큐 재처리 워커.

    - 주기: settings.dlq_reprocess_interval_seconds
    - 사이클당 최대: settings.dlq_reprocess_batch_max 건
    - 소비 방식: basic_get (polling)
    - 성공: ack / 실패: nack(requeue=True)
    """

    def __init__(self) -> None:
        self._task: asyncio.Task | None = None
        self._running = False
        self._connection: aio_pika.abc.AbstractRobustConnection | None = None

    async def start(self) -> None:
        if self._running:
            return
        self._running = True
        self._task = asyncio.create_task(self._run_loop())
        logger.info(
            "[DLQWorker] 시작 (interval=%ds, batch_max=%d, source=%s, target=%s)",
            settings.dlq_reprocess_interval_seconds,
            settings.dlq_reprocess_batch_max,
            settings.rabbitmq_dlq_name,
            settings.rabbitmq_queue,
        )

    async def stop(self) -> None:
        self._running = False
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
            self._task = None
        if self._connection and not self._connection.is_closed:
            await self._connection.close()
            self._connection = None
        logger.info("[DLQWorker] 종료 완료")

    async def _run_loop(self) -> None:
        try:
            while self._running:
                try:

                    reprocessed = await self._reprocess_cycle()
                    if reprocessed > 0:
                        logger.info("[DLQWorker] 재처리 완료 - %d건", reprocessed)
                except asyncio.CancelledError:
                    raise
                except Exception as e:
                    logger.error("[DLQWorker] 사이클 실패 - %s", e)

                if not self._running:
                    break
                await asyncio.sleep(settings.dlq_reprocess_interval_seconds)

        except asyncio.CancelledError:
            logger.info("[DLQWorker] 취소 신호 수신")
            raise

    async def _ensure_connection(self) -> aio_pika.abc.AbstractRobustConnection:
        if self._connection is None or self._connection.is_closed:
            self._connection = await aio_pika.connect_robust(settings.rabbitmq_url)
        return self._connection

    async def _reprocess_cycle(self) -> int:
        """한 사이클 재처리. 성공 건수 반환."""
        connection = await self._ensure_connection()
        channel = await connection.channel()
        try:
            await channel.set_qos(prefetch_count=settings.dlq_reprocess_batch_max)
            dlq = await channel.get_queue(settings.rabbitmq_dlq_name, ensure=True)

            success_count = 0
            for _ in range(settings.dlq_reprocess_batch_max):
                message: AbstractIncomingMessage | None = await dlq.get(
                    timeout=5.0, fail=False
                )
                if message is None:
                    break
                if await self._try_republish(channel, message):
                    success_count += 1
            return success_count
        finally:
            if not channel.is_closed:
                await channel.close()

    async def _try_republish(
        self,
        channel: aio_pika.abc.AbstractChannel,
        message: AbstractIncomingMessage,
    ) -> bool:
        """메인 큐로 재발행 + 성공 여부 반환."""
        try:
            new_message = aio_pika.Message(
                body=message.body,
                content_type=message.content_type or "application/json",
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
                headers=dict(message.headers or {}),
            )
            await channel.default_exchange.publish(
                new_message,
                routing_key=settings.rabbitmq_queue,
            )
            await message.ack()
            return True
        except Exception as e:
            logger.error("[DLQWorker] 재발행 실패, DLQ 로 반환 - %s", e)
            try:
                await message.nack(requeue=True)
            except Exception as nack_err:  # pragma: no cover - 방어적 로깅
                logger.error("[DLQWorker] nack 실패 - %s", nack_err)
            return False
