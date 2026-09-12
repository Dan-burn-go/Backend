import asyncio
import logging

from aio_pika.abc import AbstractIncomingMessage

from app.ai.errors import NonRetriableError, RetriableError
from app.ai.interface import AIAnalyzer
from app.config import settings
from app.models.schemas import CongestionEvent
from app.rabbitmq.dedup import IdempotencyGate
from app.rabbitmq.publisher import RabbitMQPublisher

logger = logging.getLogger(__name__)

# 배치 버퍼 항목 타입
BatchItem = tuple[CongestionEvent, AbstractIncomingMessage]


class BatchProcessor:
    """메시지 버퍼 → 배치 AI 분석 → 결과 발행.

    - 버퍼 항목: (event, IncomingMessage) 쌍
    - 처리 결과에 따라 ack / nack(requeue=False) 라우팅
    - nack(requeue=False): DLX 경유 DLQ 이동 (재시도는 DLQWorker 담당)
    - mode: "normal" 또는 "anomaly". analyzer 에 전달되어 시스템 프롬프트/tool_choice 분기.
    - max_size / window_seconds 인자 미지정 시 settings 기본값 사용.
      anomaly 큐는 max_size=1 로 즉시 처리.
    - gate: 멱등성 게이트. LLM 호출 전 (area_code, population_time) 선점으로 중복 요청 차단.
      None 이면 게이트 비활성(전건 fresh) — 기존 동작.
    """

    def __init__(
        self,
        analyzer: AIAnalyzer,
        publisher: RabbitMQPublisher,
        *,
        mode: str = "normal",
        max_size: int | None = None,
        window_seconds: float | None = None,
        name: str | None = None,
        gate: IdempotencyGate | None = None,
    ) -> None:
        self._analyzer = analyzer
        self._publisher = publisher
        self._gate = gate
        self._mode = mode
        self._max_size = max_size if max_size is not None else settings.batch_max_size
        self._window_seconds = (
            window_seconds if window_seconds is not None else settings.batch_window_seconds
        )
        self._name = name or mode
        self._buffer: list[BatchItem] = []
        self._lock = asyncio.Lock()
        self._timer_task: asyncio.Task | None = None
        self._running = False

    async def start(self) -> None:
        self._running = True
        self._timer_task = asyncio.create_task(self._timer_loop())
        logger.info(
            "[BatchProcessor:%s] 시작 (mode=%s, window=%ss, max_size=%d)",
            self._name,
            self._mode,
            self._window_seconds,
            self._max_size,
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
            if len(self._buffer) >= self._max_size:
                items = self._buffer.copy()
                self._buffer.clear()
        if items is not None:
            await self._process(items)

    async def _timer_loop(self) -> None:
        while self._running:
            await asyncio.sleep(self._window_seconds)
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
            except Exception as e:
                # 첫 실패면 channel 이 죽은 것 → 나머지 ack 시도 무의미
                logger.error(
                    "[BatchProcessor] ack 실패 - %s: %r",
                    type(e).__name__,
                    e,
                )
                return

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
            except Exception as e:
                logger.error(
                    "[BatchProcessor] nack 실패 - %s: %r",
                    type(e).__name__,
                    e,
                )
                return

    async def _partition(
        self, items: list[BatchItem]
    ) -> tuple[list[BatchItem], list[BatchItem]]:
        """멱등성 게이트로 (선점 성공=fresh, 이미 존재=중복) 분리.

        - gate 미설정 시 전건 fresh (기존 동작)
        - 같은 (area_code, population_time) 이 한 배치에 중복돼도 첫 건만 fresh
        """
        if self._gate is None:
            return list(items), []
        fresh: list[BatchItem] = []
        duplicates: list[BatchItem] = []
        for event, message in items:
            if await self._gate.acquire(self._gate.key_for(event)):
                fresh.append((event, message))
            else:
                duplicates.append((event, message))
        return fresh, duplicates

    async def _release_all(self, items: list[BatchItem]) -> None:
        """선점 마커 해제 (실패로 DLQ 이동 시 → DLQ 재처리가 재선점 가능)."""
        if self._gate is None:
            return
        for event, _ in items:
            await self._gate.release(self._gate.key_for(event))

    async def _process(self, items: list[BatchItem]) -> None:
        if not items:
            return

        fresh, duplicates = await self._partition(items)

        # 중복(이미 선점됨) → LLM 호출 없이 즉시 ack (토큰 절약)
        if duplicates:
            logger.info(
                "[BatchProcessor:%s] 중복 요청 %d건 스킵 - LLM 호출 없음",
                self._name,
                len(duplicates),
            )
            await self._ack_all(duplicates)

        if not fresh:
            return

        events = [event for event, _ in fresh]
        logger.info("[BatchProcessor:%s] 배치 처리 시작 - %d건", self._name, len(events))

        try:
            results = await self._analyzer.analyze(events, mode=self._mode)
        except NonRetriableError as e:
            await self._release_all(fresh)
            await self._dlq_all(fresh, f"NonRetriableError: {e}")
            return
        except RetriableError as e:
            await self._release_all(fresh)
            await self._dlq_all(fresh, f"RetriableError: {e}")
            return
        except Exception as e:
            await self._release_all(fresh)
            await self._dlq_all(fresh, f"AI 분석 실패: {type(e).__name__}: {e}")
            return

        # 분석 성공 → publish 시도
        try:
            await self._publisher.publish_all(results)
        except Exception as e:
            await self._release_all(fresh)
            await self._dlq_all(fresh, f"Publisher 실패: {e}")
            return

        # 성공 → 마커 유지(TTL 동안 재전달 스킵) 후 ack
        await self._ack_all(fresh)
        logger.info(
            "[BatchProcessor:%s] 배치 처리 완료 - 분석=%d건, 입력=%d건",
            self._name,
            len(results),
            len(fresh),
        )
