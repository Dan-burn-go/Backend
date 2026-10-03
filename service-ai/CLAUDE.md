# AI Analysis Service (service-ai, FastAPI 8085)

- **통신**: EDA — Congestion Service → RabbitMQ → AI Service → RabbitMQ(역방향)
- **역할**: AI 분석만 수행. 데이터 저장은 Congestion Service에 위임
- **외부 AI API**: 환경변수 `ai_provider`로 스위칭 (`stub` | `openai`)
- **Exchange**: `congestion.events` (topic)
- **수신 Queue**: `ai.congestion.analysis` (routing_key: `congestion.busy`)
- **발행 routing_key**: `ai.report` → Congestion Service가 `congestion.ai.report` 큐에서 수신하여 Redis 캐싱 + DB 저장
- **설정**: 환경변수 필수 (Infisical 주입) — `RABBITMQ_URL`

## 이벤트 흐름
```
Congestion Service ──(congestion.busy)──▶ AI Service ──(ai.report)──▶ Congestion Service
                                                                           │
                                                                           ├──▶ Redis (캐싱)
                                                                           └──▶ MySQL (이력)
```

## 이벤트 발행 조건
- 상태 전이: 이전 != BUSY && 현재 == BUSY (상승 엣지만)
- Congestion Service의 CongestionStateTracker에서 벌크 감지

## 배치 처리
- Consumer에서 2초 고정 윈도우로 메시지 모아 AI API 1회 호출 (JSON 배열)
- max_size(10) 도달 시 즉시 트리거

## AI 리포트
- 저장: Congestion Service가 RabbitMQ 이벤트 수신하여 Redis 캐싱 + MySQL 저장
- 조회: `GET /api/congestion/{areaCode}/ai-report` (Congestion Service 담당)
- 폴백: Redis 미스 → MySQL 6시간 이내만 (Congestion Service 담당)
- 프롬프트 컨텍스트: areaCode, congestionLevel, populationTime

## 실패 처리
- 메시지 처리 실패 시 폐기 (requeue=False)
- RabbitMQ 연결 끊김 시 지수 백오프 자동 재연결 (최대 30초)

## Function Calling (MCP)
- in-process MCP 서버 (`app/ai/mcp/server.py`) + in-memory transport 클라이언트 (`app/ai/mcp/client.py`)
- `search_web` tool: DuckDuckGo News (`ddgs`, region `kr-kr`), top 5 제목+날짜만 반환
- max_hops=1 (LLM round trip 최대 2회 고정), parallel tool calls 지원
- TPD soft limit 80% 도달 시 tools 미주입 (기본 분석만)
- 시스템 프롬프트에 KST 오늘 날짜 + 시간대×지역 매트릭스 룰 주입

## 검증 / 관측성
- 분석 1건당 INFO 구조화 로그 (`[Analysis] {...}`) — Loki 적재
- tool_called=true: tool_queries / tool_results(제목+날짜) 풍부 / false: 짧게
- 사용자가 Grafana Log Stream → 의심 케이스 드래그 → 외부 LLM 채점 (서비스 내부 검증 시스템 없음)

## DLQ 메시지별 재시도 한도
- 헤더 `x-attempt-count` 로 누적 추적
- republish 3회 초과 시 ack 영구 폐기 + ERROR 로그 (무한 루프 차단)

## 프로젝트 구조
```
service-ai/
├── main.py                    # FastAPI + lifespan (전체 연결)
├── requirements.txt
├── app/
│   ├── config.py              # pydantic-settings 환경 설정
│   ├── rabbitmq/
│   │   ├── consumer.py        # RabbitMQ Consumer (aio-pika)
│   │   ├── publisher.py       # RabbitMQ Publisher (분석 결과 발행)
│   │   └── batch.py           # 배치 윈도우 처리
│   ├── ai/
│   │   ├── interface.py       # AIAnalyzer ABC
│   │   ├── stub.py            # 테스트용 Stub
│   │   ├── openai_client.py   # OpenAI API + tool calling 루프
│   │   ├── factory.py         # 환경변수 기반 팩토리
│   │   ├── rate_limiter.py    # TPM/TPD + soft limit
│   │   └── mcp/
│   │       ├── server.py      # in-process MCP 서버 (search_web)
│   │       └── client.py      # in-memory transport 클라이언트
│   └── models/
│       └── schemas.py         # CongestionEvent, AnalysisResult
```
