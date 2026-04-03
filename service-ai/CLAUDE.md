# AI Analysis Service (service-ai, FastAPI 8085)

- **통신**: EDA — Congestion Service → RabbitMQ → AI Service → Redis + MySQL
- **외부 AI API**: 미정 (저비용 우선)
- **Exchange**: `congestion.events` (topic), routing_key: `congestion.busy`

## 이벤트 발행 조건
- 상태 전이: 이전 != BUSY && 현재 == BUSY (상승 엣지만, Redis `prev-level:{areaCode}`)

## 배치 처리
- Consumer에서 10초 윈도우로 메시지 모아 AI API 1회 호출 (JSON 배열)

## AI 리포트
- 저장: Redis TTL 4시간 + MySQL 비동기 (Write-Through)
- 조회: `GET /api/congestion/{areaCode}/ai-report` (Congestion Service)
- 폴백: Redis 미스 → MySQL 6시간 이내만
- 테이블: 원인ID, 혼잡도ID(FK), 분석 메시지
- 프롬프트 컨텍스트: areaCode, congestionLevel, populationTime

## 실패 처리
- 1~2회 재시도 (토큰 미소비 실패만), DLQ 없음

## 확장: Function Calling
- `get_nearby_events(areaCode)`: 공공데이터 축제/행사 API
- `get_weather(areaCode)`: 기상청 API
- Consumer 내부 tool 실행 레이어 추가, 구조 변경 없음
