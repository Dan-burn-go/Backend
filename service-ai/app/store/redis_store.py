import json
import logging

from redis.asyncio import Redis

from app.config import settings
from app.models.schemas import AnalysisResult

logger = logging.getLogger(__name__)

KEY_PREFIX = "ai-report:"
TTL_SECONDS = settings.redis_report_ttl_hours * 3600


class RedisStore:
    """AI 분석 결과를 Redis에 캐싱한다. (TTL 4시간)"""

    def __init__(self) -> None:
        self._redis: Redis | None = None

    async def connect(self) -> None:
        self._redis = Redis.from_url(settings.redis_url, decode_responses=True)

    async def close(self) -> None:
        if self._redis:
            await self._redis.aclose()

    async def save(self, result: AnalysisResult) -> None:
        key = KEY_PREFIX + result.area_code
        await self._redis.set(key, result.model_dump_json(), ex=TTL_SECONDS)

    async def save_all(self, results: list[AnalysisResult]) -> None:
        if not results:
            return
        pipe = self._redis.pipeline()
        for r in results:
            pipe.set(KEY_PREFIX + r.area_code, r.model_dump_json(), ex=TTL_SECONDS)
        await pipe.execute()

    async def find_by_area_code(self, area_code: str) -> AnalysisResult | None:
        data = await self._redis.get(KEY_PREFIX + area_code)
        if data is None:
            return None
        return AnalysisResult.model_validate_json(data)
