import json
import logging

import httpx

from app.ai.interface import AIAnalyzer
from app.config import settings
from app.models.schemas import AnalysisResult, CongestionEvent

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "당신은 서울시 실시간 혼잡도 데이터를 분석하는 전문가입니다. "
    "각 지역의 혼잡 원인을 간결하게 분석하세요. "
    "응답은 반드시 JSON 배열로, 각 항목에 area_code와 analysis_message 필드를 포함하세요."
)


class OpenAIAnalyzer(AIAnalyzer):
    """OpenAI API를 호출하여 혼잡 원인을 분석한다."""

    def __init__(self) -> None:
        self._client = httpx.AsyncClient(
            base_url="https://api.openai.com/v1",
            headers={"Authorization": f"Bearer {settings.openai_api_key}"},
            timeout=30.0,
        )

    async def analyze(self, events: list[CongestionEvent]) -> list[AnalysisResult]:
        user_content = json.dumps(
            [e.model_dump() for e in events], ensure_ascii=False
        )

        response = await self._client.post(
            "/chat/completions",
            json={
                "model": settings.openai_model,
                "messages": [
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": user_content},
                ],
                "response_format": {"type": "json_object"},
            },
        )
        response.raise_for_status()

        content = response.json()["choices"][0]["message"]["content"]
        parsed = json.loads(content)
        items = parsed if isinstance(parsed, list) else parsed.get("results", [])

        event_map = {e.area_code: e for e in events}
        results = []
        for item in items:
            area_code = item["area_code"]
            event = event_map.get(area_code)
            if event is None:
                continue
            results.append(
                AnalysisResult(
                    area_code=area_code,
                    congestion_level=event.congestion_level,
                    analysis_message=item["analysis_message"],
                    population_time=event.population_time,
                )
            )
        return results

    async def close(self) -> None:
        await self._client.aclose()
