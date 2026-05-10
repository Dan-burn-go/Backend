"""app.rabbitmq.dlq_worker — 메시지별 attempt count + 임계 초과 폐기 테스트."""

from __future__ import annotations

from typing import Any
from unittest.mock import AsyncMock

import pytest

from app.config import settings
from app.rabbitmq.dlq_worker import DLQWorker


class FakeDLQMessage:
    """aio_pika.abc.AbstractIncomingMessage 최소 더미."""

    def __init__(self, headers: dict[str, Any] | None = None, body: bytes = b"{}") -> None:
        self.body = body
        self.content_type = "application/json"
        self.headers = headers or {}
        self.acked = False
        self.nacked = False
        self.nack_requeue: bool | None = None

    async def ack(self) -> None:
        self.acked = True

    async def nack(self, requeue: bool = True) -> None:
        self.nacked = True
        self.nack_requeue = requeue


class FakeChannel:
    def __init__(self) -> None:
        self.default_exchange = self
        self.publish = AsyncMock()


@pytest.fixture
def worker():
    return DLQWorker()


async def test_first_republish_sets_attempt_to_one(worker):
    channel = FakeChannel()
    msg = FakeDLQMessage(headers={})

    ok = await worker._try_republish(channel, msg)

    assert ok is True
    assert msg.acked is True
    channel.publish.assert_awaited_once()
    new_msg = channel.publish.await_args.args[0]
    assert new_msg.headers["x-attempt-count"] == 1


async def test_existing_attempt_count_increments(worker):
    channel = FakeChannel()
    msg = FakeDLQMessage(headers={"x-attempt-count": 2})

    ok = await worker._try_republish(channel, msg)

    assert ok is True
    new_msg = channel.publish.await_args.args[0]
    assert new_msg.headers["x-attempt-count"] == 3


async def test_attempt_at_threshold_triggers_permanent_discard(worker, caplog):
    """attempt==message_max_attempt 도달 → ack 폐기 + ERROR 로그, republish 안 함."""
    channel = FakeChannel()
    msg = FakeDLQMessage(headers={"x-attempt-count": settings.message_max_attempt})

    import logging
    with caplog.at_level(logging.ERROR, logger="app.rabbitmq.dlq_worker"):
        ok = await worker._try_republish(channel, msg)

    assert ok is False
    assert msg.acked is True
    channel.publish.assert_not_awaited()
    assert any("재시도 한도 초과" in r.getMessage() for r in caplog.records)


async def test_invalid_attempt_header_treated_as_zero(worker):
    """헤더 값이 정수로 파싱 안 되면 0 으로 간주 후 1 로 재발행."""
    channel = FakeChannel()
    msg = FakeDLQMessage(headers={"x-attempt-count": "not-a-number"})

    ok = await worker._try_republish(channel, msg)

    assert ok is True
    new_msg = channel.publish.await_args.args[0]
    assert new_msg.headers["x-attempt-count"] == 1


async def test_publish_failure_returns_false_without_ack(worker):
    """재발행 실패 → ack 안 함, nack(requeue=True) 로 DLQ 반환."""
    channel = FakeChannel()
    channel.publish.side_effect = RuntimeError("boom")
    msg = FakeDLQMessage(headers={})

    ok = await worker._try_republish(channel, msg)

    assert ok is False
    assert msg.acked is False
    assert msg.nacked is True
    assert msg.nack_requeue is True
