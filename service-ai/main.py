import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.ai.factory import create_analyzer
from app.batch import BatchProcessor
from app.consumer import RabbitMQConsumer
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
    await mysql_store.init_tables()
    await batch_processor.start()
    await consumer.start()
    logger.info("[AI Service] 시작 완료")

    yield

    # shutdown
    await consumer.stop()
    await batch_processor.stop()
    await redis_store.close()
    await mysql_store.close()
    logger.info("[AI Service] 종료 완료")


app = FastAPI(title="Dan-burn-go AI Service", lifespan=lifespan)


@app.get("/health")
def health():
    return {"status": "ok"}
