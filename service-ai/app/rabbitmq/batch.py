import asyncio
import logging

from aio_pika.abc import AbstractIncomingMessage

from app.ai.errors import NonRetriableError, RetriableError
from app.ai.interface import AIAnalyzer
from app.config import settings
from app.models.schemas import CongestionEvent
from app.rabbitmq.publisher import RabbitMQPublisher

logger = logging.getLogger(__name__)

MAX_BATCH_RETRY = 2
RETRY_BASE_DELAY = 5.0  # 초기 대기 시간(초)

# 배치 버퍼 항목 타입
BatchItem = tuple[CongestionEvent, AbstractIncomingMessage]


class BatchProcessor:
    """메시지 버퍼 → 배치 AI 분석 → 결과 발행.

    - 버퍼 항목: (event, IncomingMessage) 쌍
    - 처리 결과에 따라 ack / nack(requeue=False) 라우팅
    - nack(requeue=False): DLX 경유 DLQ 이동
    """

    def __init__(
        self,
        analyzer: AIAnalyzer,
        publisher: RabbitMQPublisher,
    ) -> None:
        self._analyzer = analyzer
        self._publisher = publisher
        self._buffer: list[BatchItem] = []
        self._retry_count: int = 0
        self._lock = asyncio.Lock()
        self._timer_task: asyncio.Task | None = None
        self._running = False

    async def start(self) -> None:
        self._running = True
        self._timer_task = asyncio.create_task(self._timer_loop())
        logger.info(
            "[BatchProcessor] 시작 (window=%ss, max_size=%d)",
            settings.batch_window_seconds,
            settings.batch_max_size,
        )

    async def stop(self) -> None:
        self._running = False
        if self._timer_task:
            self._timer_task.cancel()
            try:
                await self._timer_task
            except asyncio.CancelledError:
                pass
        # 남은 버퍼 처리
        await self._flush()

    async def add(self, event: CongestionEvent, message: AbstractIncomingMessage) -> None:
        items: list[BatchItem] | None = None
        async with self._lock:
            self._buffer.append((event, message))
            if len(self._buffer) >= settings.batch_max_size:
                items = self._buffer.copy()
                self._buffer.clear()
        if items is not None:
            await self._process(items)

    async def _timer_loop(self) -> None:
        while self._running:
            await asyncio.sleep(settings.batch_window_seconds)
            await self._flush()

    async def _flush(self) -> None:
        async with self._lock:
            if not self._buffer:
                return
            items = self._buffer.copy()
            self._buffer.clear()
        await self._process(items)

    async def _ack_all(self, items: list[BatchItem]) -> None:
        for _event, message in items:
            try:
                await message.ack()
            except Exception as e:  # pragma: no cover - 방어적 로깅
                logger.error("[BatchProcessor] ack 실패 - %s", e)

    async def _dlq_all(self, items: list[BatchItem], reason: str) -> None:
        """nack(requeue=False) → DLX 경유 DLQ 라우팅."""
        logger.error(
            "[BatchProcessor] %d건 DLQ 라우팅 - %s",
            len(items),
            reason,
        )
        for _event, message in items:
            try:
                await message.nack(requeue=False)
            except Exception as e:  # pragma: no cover - 방어적 로깅
                logger.error("[BatchProcessor] nack 실패 - %s", e)

    async def _rebuffer(self, items: list[BatchItem]) -> None:
        async with self._lock:
            self._buffer.extend(items)

    async def _process(self, items: list[BatchItem]) -> None:
        if not items:
            return
        events = [event for event, _ in items]
        logger.info("[BatchProcessor] 배치 처리 시작 - %d건", len(events))

        try:
            results = await self._analyzer.analyze(events)
            self._retry_count = 0
        except NonRetriableError as e:
            # 재시도 금지 → 즉시 DLQ
            await self._dlq_all(items, f"NonRetriableError: {e}")
            self._retry_count = 0
            return
        except RetriableError as e:
            # retry_after 대기 후 재버퍼링
            logger.warning(
                "[BatchProcessor] RetriableError - %.1f초 후 %d건 재시도 (%s)",
                e.retry_after,
                len(items),
                e,
            )
            await asyncio.sleep(e.retry_after)
            await self._rebuffer(items)
            return
        except Exception as e:
            self._retry_count += 1
            if self._retry_count <= MAX_BATCH_RETRY:
                delay = RETRY_BASE_DELAY * (2 ** (self._retry_count - 1))
                logger.warning(
                    "[BatchProcessor] AI 분석 실패 (%d/%d) - %s, %.0f초 후 %d건 재시도",
                    self._retry_count,
                    MAX_BATCH_RETRY,
                    e,
                    delay,
                    len(items),
                )
                await asyncio.sleep(delay)
                await self._rebuffer(items)
            else:
                await self._dlq_all(
                    items,
                    f"최대 재시도 초과 ({MAX_BATCH_RETRY}회): {e}",
                )
                self._retry_count = 0
            return

        # 분석 성공 → publish 시도
        try:
            await self._publisher.publish_all(results)
        except Exception as e:
            # publish 실패 → DLQ 로 라우팅 (재처리 루프에 위임)
            await self._dlq_all(items, f"Publisher 실패: {e}")
            return

        await self._ack_all(items)
        logger.info(
            "[BatchProcessor] 배치 처리 완료 - 분석=%d건, 입력=%d건",
            len(results),
            len(items),
        )
