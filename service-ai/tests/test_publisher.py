"""app.rabbitmq.publisher — RabbitMQPublisher.publish_all() 로직 테스트.

실제 RabbitMQ 연결 없이 exchange.publish 호출을 mock으로 검증한다.
"""

from __future__ import annotations

import json
from unittest.mock import AsyncMock, MagicMock

import pytest

from app.models.schemas import AnalysisResult
from app.rabbitmq.publisher import REPORT_ROUTING_KEY, RabbitMQPublisher


def _result(code: str = "A") -> AnalysisResult:
    return AnalysisResult(
        area_name=f"지역-{code}",
        area_code=code,
        congestion_level="BUSY",
        analysis_message="분석 결과",
        population_time="2026-05-28 12:00",
    )


class TestPublishAll:
    @pytest.mark.asyncio
    async def test_empty_list_does_not_call_exchange(self):
        publisher = RabbitMQPublisher()
        mock_exchange = AsyncMock()
        publisher._exchange = mock_exchange
        await publisher.publish_all([])
        mock_exchange.publish.assert_not_called()

    @pytest.mark.asyncio
    async def test_single_result_published_with_correct_routing_key(self):
        publisher = RabbitMQPublisher()
        mock_exchange = AsyncMock()
        publisher._exchange = mock_exchange

        await publisher.publish_all([_result("X")])

        mock_exchange.publish.assert_called_once()
        _, kwargs = mock_exchange.publish.call_args
        assert kwargs.get("routing_key") == REPORT_ROUTING_KEY

    @pytest.mark.asyncio
    async def test_multiple_results_each_published(self):
        publisher = RabbitMQPublisher()
        mock_exchange = AsyncMock()
        publisher._exchange = mock_exchange

        results = [_result("A"), _result("B"), _result("C")]
        await publisher.publish_all(results)

        assert mock_exchange.publish.call_count == 3

    @pytest.mark.asyncio
    async def test_message_body_contains_correct_fields(self):
        publisher = RabbitMQPublisher()
        published_messages: list = []

        async def capture_publish(message, *, routing_key):
            published_messages.append(message)

        mock_exchange = MagicMock()
        mock_exchange.publish = capture_publish
        publisher._exchange = mock_exchange

        r = _result("Z")
        await publisher.publish_all([r])

        assert len(published_messages) == 1
        body = json.loads(published_messages[0].body.decode())
        assert body["areaCode"] == "Z"
        assert body["areaName"] == r.area_name
        assert body["congestionLevel"] == r.congestion_level
        assert body["analysisMessage"] == r.analysis_message
        assert body["populationTime"] == r.population_time

    @pytest.mark.asyncio
    async def test_close_safe_when_not_connected(self):
        publisher = RabbitMQPublisher()
        # _channel/_connection 모두 None인 상태에서 close() 안전 완료
        await publisher.close()
