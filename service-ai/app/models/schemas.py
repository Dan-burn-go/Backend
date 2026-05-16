from pydantic import BaseModel


class CongestionEvent(BaseModel):
    area_name: str
    area_code: str
    congestion_level: str
    max_people_count: int
    population_time: str
    # anomaly 메시지에서만 실려옴 (일반 메시지엔 None)
    avg_max_people: float | None = None
    ratio: float | None = None


class AnalysisResult(BaseModel):
    area_name: str
    area_code: str
    congestion_level: str
    analysis_message: str
    population_time: str
