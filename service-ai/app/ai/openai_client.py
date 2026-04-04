import json
import logging
import re

import httpx

from app.ai.interface import AIAnalyzer
from app.config import settings
from app.models.schemas import AnalysisResult, CongestionEvent

logger = logging.getLogger(__name__)

SYSTEM_PROMPT = (
    "당신은 서울시 실시간 혼잡도 데이터를 분석하는 전문가입니다. "
    "각 지역의 혼잡 원인을 지역 특성(상권, 교통, 관광지 등)과 "
    "시간대 맥락(출근길, 점심시간, 퇴근길, 저녁 약속, 심야 등)을 고려하여 간결하게 분석하세요. "
    '응답은 반드시 {"results": [...]} 형태의 JSON 객체로, '
    "각 항목에 area_code, area_name, analysis_message 필드를 포함하세요."
)


class OpenAIAnalyzer(AIAnalyzer):
    """OpenAI API를 호출하여 혼잡 원인을 분석한다."""

    def __init__(self) -> None:
        self._client = httpx.AsyncClient(
            base_url=settings.openai_base_url,
            headers={"Authorization": f"Bearer {settings.openai_api_key}"},
            timeout=httpx.Timeout(60.0, read=300.0),
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
                    {"role": "user", "content": f"{user_content}\n\n결과는 Markdown 없이 순수 JSON 객체로만 응답하세요."},
                ],
                # Ollama/Qwen 호환: response_format은 OpenAI 전용이므로 프롬프트로 JSON 출력 유도
            },
        )
        response.raise_for_status()

        try:
            content = response.json()["choices"][0]["message"]["content"]
            # 마크다운 코드블록 제거 방어
            match = re.search(r'```(?:json)?\s*(.*?)```', content, re.DOTALL)
            if match:
                content = match.group(1)
            parsed = json.loads(content.strip())
            if isinstance(parsed, list):
                parsed = {"results": parsed}
            elif not isinstance(parsed, dict):
                parsed = {"results": []}
        except (KeyError, IndexError, json.JSONDecodeError) as e:
            logger.error("[OpenAI] 응답 파싱 실패 - %s", e)
            raise

        items = parsed.get("results", parsed.get("data", []))

        event_map = {e.area_code: e for e in events}
        results = []
        for item in items:
            area_code = item.get("area_code")
            analysis_message = item.get("analysis_message")
            if not area_code or not analysis_message:
                continue
            event = event_map.get(area_code)
            if event is None:
                continue
            results.append(
                AnalysisResult(
                    area_name=event.area_name,
                    area_code=area_code,
                    congestion_level=event.congestion_level,
                    analysis_message=analysis_message,
                    population_time=event.population_time,
                )
            )
        return results

    async def close(self) -> None:
        await self._client.aclose()
