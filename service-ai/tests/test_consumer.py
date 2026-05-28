"""app.rabbitmq.consumer — RabbitMQConsumer._on_message() 파싱 로직 테스트.

실제 RabbitMQ 연결 없이 _on_message() 의 파싱/nack 분기만 검증한다.
"""

from __future__ import annotations

import json

import pytest

from app.rabbitmq.consumer import RabbitMQConsumer


class FakeMessage:
    """AbstractIncomingMessage 최소 더미."""

    def __init__(self, payload: bytes) -> None:
        self.body = payload
        self.nacked = False
        self.nack_requeue: bool | None = None

    async def nack(self, requeue: bool = True) -> None:
        self.nacked = True
        self.nack_requeue = requeue


class FakeBatchProcessor:
    def __init__(self) -> None:
        self.added: list = []

    async def add(self, event, message) -> None:
        self.added.append((event, message))


def _make_consumer() -> tuple[RabbitMQConsumer, FakeBatchProcessor]:
    bp = FakeBatchProcessor()
    consumer = RabbitMQConsumer(batch_processor=bp)  # type: ignore[arg-type]
    return consumer, bp


def _valid_body(**overrides) -> bytes:
    data = {
        "areaName": "강남",
        "areaCode": "POI001",
        "congestionLevel": "BUSY",
        "maxPeopleCount": 200,
        "populationTime": "2026-05-28 09:00",
    }
    data.update(overrides)
    return json.dumps(data).encode()


class TestOnMessage:
    @pytest.mark.asyncio
    async def test_valid_message_forwarded_to_batch(self):
        consumer, bp = _make_consumer()
        msg = FakeMessage(_valid_body())
        await consumer._on_message(msg)
        assert len(bp.added) == 1
        event, forwarded_msg = bp.added[0]
        assert event.area_code == "POI001"
        assert forwarded_msg is msg
        assert not msg.nacked

    @pytest.mark.asyncio
    async def test_optional_fields_parsed(self):
        consumer, bp = _make_consumer()
        msg = FakeMessage(_valid_body(avgMaxPeople=150.0, ratio=1.33))
        await consumer._on_message(msg)
        event, _ = bp.added[0]
        assert event.avg_max_people == 150.0
        assert event.ratio == pytest.approx(1.33)

    @pytest.mark.asyncio
    async def test_invalid_json_nacks_to_dlq(self):
        consumer, bp = _make_consumer()
        msg = FakeMessage(b"not-json")
        await consumer._on_message(msg)
        assert msg.nacked is True
        assert msg.nack_requeue is False
        assert len(bp.added) == 0

    @pytest.mark.asyncio
    async def test_missing_required_field_nacks_to_dlq(self):
        consumer, bp = _make_consumer()
        # maxPeopleCount 누락
        bad = {
            "areaName": "홍대",
            "areaCode": "POI002",
            "congestionLevel": "NORMAL",
            "populationTime": "2026-05-28 12:00",
        }
        msg = FakeMessage(json.dumps(bad).encode())
        await consumer._on_message(msg)
        assert msg.nacked is True
        assert msg.nack_requeue is False
        assert len(bp.added) == 0

    @pytest.mark.asyncio
    async def test_stop_sets_running_false(self):
        consumer, _ = _make_consumer()
        consumer._running = True
        # _channel/_connection 없어도 stop() 가 안전하게 완료
        await consumer.stop()
        assert consumer._running is False
