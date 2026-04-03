import logging
from datetime import datetime, timezone

from sqlalchemy import Column, DateTime, Integer, String, Text, func
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase

from app.config import settings
from app.models.schemas import AnalysisResult

logger = logging.getLogger(__name__)


class Base(DeclarativeBase):
    pass


class AiReport(Base):
    __tablename__ = "ai_report"

    id = Column("ai_report_id", Integer, primary_key=True, autoincrement=True)
    area_code = Column(String(50), nullable=False, index=True)
    congestion_level = Column(String(20), nullable=False)
    analysis_message = Column(Text, nullable=False)
    population_time = Column(String(30), nullable=False)
    created_at = Column(DateTime, server_default=func.now())
    updated_at = Column(DateTime, server_default=func.now(), onupdate=func.now())
    deleted_at = Column(DateTime)


class MySQLStore:
    """AI 분석 결과를 MySQL에 이력으로 저장한다."""

    def __init__(self) -> None:
        self._engine = create_async_engine(settings.mysql_url, pool_size=5)
        self._session_factory = async_sessionmaker(self._engine, expire_on_commit=False)

    async def close(self) -> None:
        await self._engine.dispose()

    async def save_all(self, results: list[AnalysisResult]) -> None:
        if not results:
            return
        async with self._session_factory() as session:
            async with session.begin():
                for r in results:
                    session.add(
                        AiReport(
                            area_code=r.area_code,
                            congestion_level=r.congestion_level,
                            analysis_message=r.analysis_message,
                            population_time=r.population_time,
                        )
                    )
