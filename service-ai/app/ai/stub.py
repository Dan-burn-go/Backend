from app.ai.interface import AIAnalyzer
from app.models.schemas import AnalysisResult, CongestionEvent


class StubAnalyzer(AIAnalyzer):
    """테스트용 Stub — 고정 메시지를 반환한다."""

    async def analyze(self, events: list[CongestionEvent]) -> list[AnalysisResult]:
        return [
            AnalysisResult(
                area_name=e.area_name,
                area_code=e.area_code,
                congestion_level=e.congestion_level,
                analysis_message=f"[Stub] {e.area_name}({e.area_code}) 지역이 {e.congestion_level} 상태입니다. "
                                 f"인근 행사 및 출퇴근 시간대 영향으로 추정됩니다.",
                population_time=e.population_time,
            )
            for e in events
        ]
