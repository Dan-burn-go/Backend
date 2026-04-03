import asyncio
import logging

from app.ai.interface import AIAnalyzer
from app.config import settings
from app.models.schemas import CongestionEvent
from app.store.mysql_store import MySQLStore
from app.store.redis_store import RedisStore

logger = logging.getLogger(__name__)

MAX_BATCH_RETRY = 2


class BatchProcessor:
    """메시지를 버퍼에 모아 배치로 AI 분석 → 결과 저장한다."""

    def __init__(
        self,
        analyzer: AIAnalyzer,
        redis_store: RedisStore,
        mysql_store: MySQLStore,
    ) -> None:
        self._analyzer = analyzer
        self._redis_store = redis_store
        self._mysql_store = mysql_store
        self._buffer: list[CongestionEvent] = []
        self._retry_count: int = 0
        self._lock = asyncio.Lock()
        self._timer_task: asyncio.Task | None = None
        self._running = False

    async def start(self) -> None:
        self._running = True
        self._timer_task = asyncio.create_task(self._timer_loop())
        logger.info("[BatchProcessor] 시작 (window=%ss, max_size=%d)",
                    settings.batch_window_seconds, settings.batch_max_size)

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

    async def add(self, event: CongestionEvent) -> None:
        should_flush = False
        async with self._lock:
            self._buffer.append(event)
            if len(self._buffer) >= settings.batch_max_size:
                events = self._buffer.copy()
                self._buffer.clear()
                should_flush = True
        if should_flush:
            await self._process(events)

    async def _timer_loop(self) -> None:
        while self._running:
            await asyncio.sleep(settings.batch_window_seconds)
            await self._flush()

    async def _flush(self) -> None:
        async with self._lock:
            if not self._buffer:
                return
            events = self._buffer.copy()
            self._buffer.clear()
        await self._process(events)

    async def _process(self, events: list[CongestionEvent]) -> None:
        logger.info("[BatchProcessor] 배치 처리 시작 - %d건", len(events))
        try:
            results = await self._analyzer.analyze(events)
            self._retry_count = 0
        except Exception as e:
            self._retry_count += 1
            if self._retry_count <= MAX_BATCH_RETRY:
                logger.warning("[BatchProcessor] AI 분석 실패 (%d/%d) - %s, %d건 재삽입",
                               self._retry_count, MAX_BATCH_RETRY, e, len(events))
                async with self._lock:
                    self._buffer.extend(events)
            else:
                logger.error("[BatchProcessor] AI 분석 최종 실패 - %s, %d건 폐기", e, len(events))
                self._retry_count = 0
            return
        try:
            await self._redis_store.save_all(results)
        except Exception as e:
            logger.error("[BatchProcessor] Redis 저장 실패 - %s", e)
        try:
            await self._mysql_store.save_all(results)
        except Exception as e:
            logger.error("[BatchProcessor] MySQL 저장 실패 - %s", e)
        logger.info("[BatchProcessor] 배치 처리 완료 - %d건", len(results))
