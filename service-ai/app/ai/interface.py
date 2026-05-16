from abc import ABC, abstractmethod

from app.models.schemas import AnalysisResult, CongestionEvent


class AIAnalyzer(ABC):
    """AI 혼잡 원인 분석 인터페이스"""

    @abstractmethod
    async def analyze(
        self,
        events: list[CongestionEvent],
        *,
        mode: str = "normal",
    ) -> list[AnalysisResult]:
        """혼잡도 이벤트 배치를 받아 원인 분석 결과를 반환한다.

        mode:
          - "normal": 일반 분석. tools 조건부 주입.
          - "anomaly": 이상 패턴 분석. SYSTEM_PROMPT_ANOMALY + tool_choice='required'.
        """

    async def close(self) -> None:
        """리소스 정리. 기본 구현은 아무것도 하지 않음."""
