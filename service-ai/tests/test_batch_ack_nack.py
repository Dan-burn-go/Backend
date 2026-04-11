"""app.rabbitmq.batch — BatchProcessor ack/nack/재시도 동작 테스트."""

from __future__ import annotations

import pytest

from app.ai.errors import NonRetriableError, RetriableError
from app.models.schemas import AnalysisResult, CongestionEvent
from app.rabbitmq.batch import BatchProcessor


class FakeMessage:
    """aio_pika.abc.AbstractIncomingMessage 최소 더미 구현."""

    def __init__(self, tag: int) -> None:
        self.tag = tag
        self.acked = False
        self.nacked = False
        self.nack_requeue: bool | None = None

    async def ack(self) -> None:
        self.acked = True

    async def nack(self, requeue: bool = True) -> None:
        self.nacked = True
        self.nack_requeue = requeue


class FakePublisher:
    def __init__(self, should_fail: bool = False) -> None:
        self.should_fail = should_fail
        self.published: list[AnalysisResult] = []

    async def publish_all(self, results: list[AnalysisResult]) -> None:
        if self.should_fail:
            raise RuntimeError("publish boom")
        self.published.extend(results)


class FakeAnalyzer:
    def __init__(self, behaviour: str = "ok") -> None:
        self.behaviour = behaviour
        self.calls = 0

    async def analyze(self, events: list[CongestionEvent]) -> list[AnalysisResult]:
        self.calls += 1
        if self.behaviour == "non_retriable":
            raise NonRetriableError(error_code="queue_exceeded", message="full")
        if self.behaviour == "retriable_then_ok":
            if self.calls == 1:
                raise RetriableError(
                    error_code="token_quota_exceeded",
                    message="tpm",
                    retry_after=0.01,
                )
            return [self._result(e) for e in events]
        if self.behaviour == "unknown_always":
            raise RuntimeError("network boom")
        return [self._result(e) for e in events]

    @staticmethod
    def _result(event: CongestionEvent) -> AnalysisResult:
        return AnalysisResult(
            area_name=event.area_name,
            area_code=event.area_code,
            congestion_level=event.congestion_level,
            analysis_message="ok",
            population_time=event.population_time,
        )


def _event(code: str) -> CongestionEvent:
    return CongestionEvent(
        area_name=f"area-{code}",
        area_code=code,
        congestion_level="BUSY",
        max_people_count=100,
        population_time="2026-04-11 12:00",
    )


@pytest.mark.asyncio
async def test_success_path_acks_all_messages():
    analyzer = FakeAnalyzer("ok")
    publisher = FakePublisher()
    bp = BatchProcessor(analyzer, publisher)  # type: ignore[arg-type]

    items = [(_event("A"), FakeMessage(1)), (_event("B"), FakeMessage(2))]
    await bp._process(list(items))

    assert all(msg.acked for _, msg in items)
    assert not any(msg.nacked for _, msg in items)
    assert len(publisher.published) == 2


@pytest.mark.asyncio
async def test_non_retriable_error_nacks_to_dlq():
    analyzer = FakeAnalyzer("non_retriable")
    publisher = FakePublisher()
    bp = BatchProcessor(analyzer, publisher)  # type: ignore[arg-type]

    items = [(_event("A"), FakeMessage(1)), (_event("B"), FakeMessage(2))]
    await bp._process(list(items))

    for _, msg in items:
        assert msg.nacked is True
        assert msg.nack_requeue is False  # DLX 라우팅 → requeue=False
        assert msg.acked is False


@pytest.mark.asyncio
async def test_retriable_error_rebuffers_without_ack_or_nack():
    analyzer = FakeAnalyzer("retriable_then_ok")
    publisher = FakePublisher()
    bp = BatchProcessor(analyzer, publisher)  # type: ignore[arg-type]

    msg_a = FakeMessage(1)
    msg_b = FakeMessage(2)
    items = [(_event("A"), msg_a), (_event("B"), msg_b)]
    await bp._process(list(items))

    # 첫 처리 → re-buffer → ack/nack 둘 다 없음
    assert not msg_a.acked and not msg_a.nacked
    assert not msg_b.acked and not msg_b.nacked
    # 버퍼 재삽입 확인
    async with bp._lock:
        assert len(bp._buffer) == 2


@pytest.mark.asyncio
async def test_unknown_exception_retries_then_dlqs(monkeypatch):
    analyzer = FakeAnalyzer("unknown_always")
    publisher = FakePublisher()
    bp = BatchProcessor(analyzer, publisher)  # type: ignore[arg-type]

    # 재시도 대기 시간 0 으로 단축
    async def fast_sleep(_):
        return None

    monkeypatch.setattr("app.rabbitmq.batch.asyncio.sleep", fast_sleep)

    msg_a = FakeMessage(1)
    items = [(_event("A"), msg_a)]

    # 1차 _process: 첫 실패 → rebuffer
    await bp._process(list(items))
    assert not msg_a.acked and not msg_a.nacked
    async with bp._lock:
        assert len(bp._buffer) == 1
        bp._buffer.clear()

    # 2차 _process: 두 번째 실패 → rebuffer
    await bp._process(list(items))
    assert not msg_a.acked and not msg_a.nacked
    async with bp._lock:
        bp._buffer.clear()

    # 3차 _process: MAX_BATCH_RETRY=2 초과 → DLQ
    await bp._process(list(items))
    assert msg_a.nacked is True
    assert msg_a.nack_requeue is False


@pytest.mark.asyncio
async def test_publisher_failure_dlqs_successful_batch():
    """분석 성공 + publish 실패 → DLQ 라우팅."""
    analyzer = FakeAnalyzer("ok")
    publisher = FakePublisher(should_fail=True)
    bp = BatchProcessor(analyzer, publisher)  # type: ignore[arg-type]

    msg = FakeMessage(1)
    items = [(_event("A"), msg)]
    await bp._process(list(items))

    assert msg.nacked is True
    assert msg.nack_requeue is False
    assert msg.acked is False
