import asyncio
import json
import logging
from contextlib import asynccontextmanager

import aio_pika
from fastapi import FastAPI

from app.ai.factory import create_analyzer
from app.config import settings
from app.rabbitmq.batch import BatchProcessor
from app.rabbitmq.consumer import RabbitMQConsumer
from app.store.mysql_store import MySQLStore
from app.store.redis_store import RedisStore

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
logger = logging.getLogger(__name__)

analyzer = create_analyzer()
redis_store = RedisStore()
mysql_store = MySQLStore()
batch_processor = BatchProcessor(analyzer, redis_store, mysql_store)
consumer = RabbitMQConsumer(batch_processor)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # startup
    await redis_store.connect()
    await batch_processor.start()
    await consumer.start()
    logger.info("[AI Service] 시작 완료")

    yield

    # shutdown
    await consumer.stop()
    await batch_processor.stop()
    await analyzer.close()
    await redis_store.close()
    await mysql_store.close()
    logger.info("[AI Service] 종료 완료")


app = FastAPI(title="Dan-burn-go AI Service", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok"}


# ── 테스트 전용 API (프로덕션에서 제거) ──

@app.post("/test/publish")
async def test_publish(area_code: str = "POI001", congestion_level: str = "붐빔"):
    """RabbitMQ에 테스트 메시지를 발행한다. Consumer → Batch → Stub AI 전체 플로우 검증용."""
    connection = await aio_pika.connect_robust(settings.rabbitmq_url)
    async with connection:
        channel = await connection.channel()
        message = aio_pika.Message(
            body=json.dumps({
                "areaCode": area_code,
                "congestionLevel": congestion_level,
                "maxPeopleCount": 12000,
                "populationTime": "2026-04-03 14:00",
            }).encode(),
            content_type="application/json",
        )
        await channel.default_exchange.publish(message, routing_key=settings.rabbitmq_queue)
    return {"status": "published", "area_code": area_code, "congestion_level": congestion_level}


@app.post("/test/analyze")
async def test_analyze():
    """Cerebras API 직접 호출 테스트. 더미 이벤트 2건으로 분석 결과를 반환한다."""
    from app.models.schemas import CongestionEvent

    dummy_events = [
        CongestionEvent(area_name="강남역", area_code="POI001", congestion_level="BUSY", max_people_count=50000, population_time="2026-04-04T14:30:00"),
        CongestionEvent(area_name="홍대입구역", area_code="POI002", congestion_level="BUSY", max_people_count=32000, population_time="2026-04-04T14:30:00"),
    ]
    results = await analyzer.analyze(dummy_events)
    return {"results": [r.model_dump() for r in results]}
