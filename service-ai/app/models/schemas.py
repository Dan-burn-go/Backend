from pydantic import BaseModel


class CongestionEvent(BaseModel):
    area_name: str
    area_code: str
    congestion_level: str
    max_people_count: int
    population_time: str


class AnalysisResult(BaseModel):
    area_name: str
    area_code: str
    congestion_level: str
    analysis_message: str
    population_time: str
