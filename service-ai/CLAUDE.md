# AI Analysis Service (service-ai, FastAPI 8085)

- **통신**: EDA — Congestion Service → RabbitMQ → AI Service → Redis + MySQL
- **외부 AI API**: 환경변수 `ai_provider`로 스위칭 (`stub` | `openai`)
- **Exchange**: `congestion.events` (topic), routing_key: `congestion.busy`
- **Queue**: `ai.congestion.analysis`

## 이벤트 발행 조건
- 상태 전이: 이전 != BUSY && 현재 == BUSY (상승 엣지만)
- Congestion Service의 CongestionStateTracker에서 벌크 감지

## 배치 처리
- Consumer에서 10초 윈도우로 메시지 모아 AI API 1회 호출 (JSON 배열)
- max_size(50) 도달 시 즉시 트리거

## AI 리포트
- 저장: Redis TTL 4시간 + MySQL 비동기 (Write-Through)
- 조회: `GET /api/congestion/{areaCode}/ai-report` (Congestion Service 담당)
- 폴백: Redis 미스 → MySQL 6시간 이내만 (Congestion Service 담당)
- 테이블: `ai_report` (id, area_code, congestion_level, analysis_message, population_time, created_at)
- 프롬프트 컨텍스트: areaCode, congestionLevel, populationTime

## 실패 처리
- 메시지 처리 실패 시 최대 2회 재시도, 초과 시 폐기 (requeue=False)
- RabbitMQ 연결 끊김 시 지수 백오프 자동 재연결 (최대 30초)

## 확장: Function Calling
- `get_nearby_events(areaCode)`: 공공데이터 축제/행사 API
- `get_weather(areaCode)`: 기상청 API
- Consumer 내부 tool 실행 레이어 추가, 구조 변경 없음

## 프로젝트 구조
```
service-ai/
├── main.py                    # FastAPI + lifespan (전체 연결)
├── requirements.txt
├── app/
│   ├── config.py              # pydantic-settings 환경 설정
│   ├── consumer.py            # RabbitMQ Consumer (aio-pika)
│   ├── batch.py               # 배치 윈도우 처리
│   ├── ai/
│   │   ├── interface.py       # AIAnalyzer ABC
│   │   ├── stub.py            # 테스트용 Stub
│   │   ├── openai_client.py   # OpenAI API 호출
│   │   └── factory.py         # 환경변수 기반 팩토리
│   ├── models/
│   │   └── schemas.py         # CongestionEvent, AnalysisResult
│   └── store/
│       ├── redis_store.py     # Redis 캐싱 (TTL 4h)
│       └── mysql_store.py     # MySQL 이력 저장 (SQLAlchemy async)
```
