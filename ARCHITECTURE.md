# Architecture

## 모듈
| 모듈 | 포트 | 역할 | 저장소 / 인프라 |
|---|---|---|---|
| service-gateway | 8080 | 라우팅 (Spring Cloud Gateway) | 없음 |
| service-congestion | 8082 | 혼잡도 수집·저장·제공, AI 리포트 저장 일원화 | Redis(캐싱) + MySQL `danburn_congestion` |
| service-map | 8083 | 반경 내 대체 장소 탐색·추천 | MySQL `danburn_map` (Spatial Index) |
| service-mobility | 8084 | 교통 경로 추천 | 없음 (외부 API만) |
| service-ai | 8085 | AI 혼잡 원인 분석 (Python FastAPI) | RabbitMQ 수신/발행, 저장은 congestion 에 위임 |
| service-common | - | 공유 라이브러리 (`ApiResponse`, 공통 예외) | - |

`settings.gradle` 은 Java 5개 모듈만 포함한다. `service-ai` 는 Python 이라 Gradle 이 관리하지 않고 자체 `Dockerfile` 과 `requirements.txt` 로 빌드한다.

## 레이어
Java 모듈은 `com.danburn.<module>` 아래 같은 구성을 쓴다.

- `controller` — HTTP 경계. 요청 검증, `ApiResponse` 로 응답 감싸기
- `service` — 도메인 로직. 트랜잭션 경계
- `repository` — JPA 영속화
- `infra` — 외부 API 클라이언트 (서울시 공공 API, ODsay 등). API Key 미설정 시 Stub 구현으로 대체
- `event` — RabbitMQ consumer / publisher
- `scheduler` — 주기 수집, 정리 크론
- `domain`, `dto` — 엔티티와 요청·응답 모델
- `config` — Redis, RabbitMQ, Async 설정

`service-congestion` 의 `notification` 은 같은 모듈 안의 독립 하위 도메인으로, 자체 controller·service·repository 를 가진다.

## 의존 방향
- `controller` → `service` → `repository` / `infra`. controller 가 repository 를 직접 호출하지 않는다
- 모든 모듈 → `service-common` (단방향). `service-common` 은 어떤 모듈도 참조하지 않는다
- 서비스 간 동기 호출은 `service-map` → `service-congestion` 하나뿐이고, Circuit Breaker + TimeLimiter + Redis 캐시 폴백을 반드시 끼운다 (`docs/design-docs/decisions/003-circuit-breaker-msa-call.md`)
- `service-congestion` ↔ `service-ai` 는 동기 호출이 없다. RabbitMQ 양방향 이벤트만 쓴다
- `service-ai` 는 DB 에 직접 쓰지 않는다. 분석만 하고 결과를 이벤트로 돌려보낸다

## 핵심 흐름
```
[수집] 서울시 공공 API ──5분 크론──▶ congestion ──▶ Redis(15분 TTL) + MySQL(@Async 이력)

[AI 분석 — 양방향 EDA]
congestion ──(congestion.busy)──▶ [congestion.events / topic] ──▶ ai.congestion.analysis ──▶ service-ai
service-ai ──(ai.report)──▶ congestion.ai.report ──▶ congestion ──▶ Redis 캐싱 + MySQL 저장

[대체지] map ──MySQL ST_Distance_Sphere 2km──▶ 후보 ──외부 길찾기 API 병렬──▶ 소요시간 순
[경로]   mobility ──외부 버스 API──▶ 소요시간 순
```

이벤트 발행 조건은 상승 엣지만이다. 이전 상태 != BUSY && 현재 == BUSY 일 때 `CongestionStateTracker` 가 벌크 감지한다. 모든 수집 주기마다 발행하지 않는 이유는 `docs/design-docs/decisions/001-rabbitmq-eda.md`.

## 외부 연동
- MySQL 8.0 — `danburn_congestion`, `danburn_map`. DB·사용자 생성은 `mysql/init/` (최초 기동 전용) + CD 의 `Ensure MySQL DB users exist` 스텝
- Redis 7 — 혼잡도 캐시(15분 TTL), AI 리포트 캐시, 중복 LLM 요청 방지 분산락
- RabbitMQ — `congestion.events` topic exchange, DLQ 포함. 메트릭은 15692 포트
- 외부 API — 서울시 공공 API(혼잡도), ODsay(경로), Cerebras/OpenAI 호환 LLM, DuckDuckGo News(MCP `search_web`)
- 관측성 — OTel Collector(4317/4318) → Grafana + Prometheus + Loki (`observability` 컨테이너, 3000)
- 환경변수는 Infisical 이 주입한다. `.env` 를 저장소에 두지 않는다

## 배포
- CI: `.github/workflows/ci.yml` — 모듈별 `./gradlew :<module>:build` + Docker 빌드 테스트
- CD: `.github/workflows/cd.yml` — main push 또는 `workflow_dispatch`. 이미지 빌드는 GitHub 호스티드, `deploy` 잡은 **운영 서버 자신(self-hosted runner)** 에서 실행된다. 서버가 내려가면 러너도 offline 이라 CD 로는 복구할 수 없다 (`docs/design-docs/failures/001-disk-full-outage.md`)
