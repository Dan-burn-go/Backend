# Dan-burn-go Backend — Product Requirements Document (PRD)

> **작성일:** 2026-04-03 | **버전:** 1.4

---

## 1. Executive Summary

**Dan-burn-go**는 서울의 주요 관광지·번화가의 **실시간 혼잡도 데이터**를 수집·분석하여 사용자에게 대체 장소와 교통 정보를 추천하는 마이크로서비스 백엔드입니다.

**핵심 가치:** 서울시 공공 API와 연동해 122개 주요 장소의 실시간 인구 혼잡도를 제공하고, AI 기반 예측과 대체 경로를 추천합니다.

**기술 스택:**

- Java 21, Spring Boot 3.4.5, Spring Cloud 2024.0.2
- MySQL 8.0 (Spatial Index), Redis 7
- RabbitMQ (메시지 큐)
- Python 3.12 (FastAPI) — AI Analysis Service
- Docker Compose
- Monitoring: Grafana + Prometheus + cAdvisor → Slack 알림
- Logging: Grafana + Grafana Loki
- Frontend: Vercel (React)

---

## 2. System Architecture

### 2.1 모듈 구조

| 모듈 | 포트 | 역할 | DB / 인프라 |
| --- | --- | --- | --- |
| **API Gateway** | 8080 | 라우팅 | 없음 |
| **Congestion Analysis Service** | 8082 | 혼잡도 수집·저장·제공 | Redis (캐싱) + MySQL (이력) |
| **Map Service** | 8083 | 장소 탐색·추천 | MySQL (Spatial Index) |
| **Mobility Service** | 8084 | 교통 경로 추천 | 없음 (외부 API만 사용) |
| **AI Analysis Service** | 8085 | AI 혼잡 원인 분석 | RabbitMQ 수신 → 외부 AI API → RabbitMQ 역방향 발행 (저장은 Congestion Service 위임) |
| **service-common** | - | 공유 라이브러리 | - |

### 2.2 서비스 간 통신 흐름

```
[데이터 수집]
서울시 공공 API → Congestion Service → Redis 캐싱 + MySQL 이력

[서비스 간 비동기 통신 — 양방향 EDA]
Congestion Service → (BUSY 상승 엣지 감지) → RabbitMQ → AI Service (분석만 수행)
AI Service → (분석 결과) → RabbitMQ → Congestion Service → Redis 캐싱 + MySQL 저장

[독립 서비스 — 다른 서비스 호출 없음]
Map Service      → MySQL Spatial 후보 추출 → 외부 길찾기 API → 소요시간 순 정렬
Mobility Service → 외부 버스 API → 소요시간 순 경로 추천

[AI 리포트 조회]
클라이언트 → Congestion Service API → Redis에서 AI 리포트 읽어서 제공

[프론트엔드 조합]
프론트엔드 → Congestion API → 혼잡도 데이터 표시 (지도 Heatmap 등)
프론트엔드 → Map API → 대체지 후보 반환
프론트엔드 → Mobility API → 버스 경로 반환
```

### 2.3 Observability

- **grafana/otel-lgtm** 단일 컨테이너 (Grafana + Prometheus + Loki + OTel Collector 내장)
- 서비스 4~5개 규모에서는 단일 컨테이너로 충분하다 판단

---

## 3. 모듈 상세 명세

### 3.1 service-common (공유 라이브러리)

| 클래스 | 역할 |
| --- | --- |
| `BaseEntity` | JPA 기본 엔티티 (createdAt, updatedAt, deletedAt) |
| `ApiResponse<T>` | 통일된 API 응답 래퍼 |
| `GlobalException` | HTTP 상태 코드 기반 커스텀 예외 |

### 3.2 Congestion Analysis Service (혼잡도 분석)

**DB:** Redis (실시간 캐싱, 15분 TTL) + MySQL (7일 이력 저장)

**역할:** 혼잡도 데이터 수집·저장·제공에 집중. AI 리포트도 이 서비스를 통해 클라이언트에 제공.

**도메인 모델:**

```
Congestion
├── areaCode (String, 122개 서울 주요 장소)
├── congestionLevel (RELAXED | NORMAL | SLIGHTLY_CROWDED | BUSY)
├── congestionMessage (String)
├── minPeopleCount / maxPeopleCount (Integer)
├── populationTime (LocalDateTime)
└── forecast (JSON, 3시간 예보)
```

**현재 구현된 기능:**

- 서울시 API에서 5분 간격으로 122개 장소 혼잡도 데이터 수집
- Redis 캐싱 (15분 TTL, 파이프라인 배치 저장)
- MySQL 이력 저장 (비동기 `@Async` 백그라운드 처리, 7일 보관)
- 데이터 정리 (매일 03:00 크론으로 7일 이상 데이터 삭제)
- Stub 클라이언트 (API Key 미설정 시 더미 데이터 제공, 개발용)

**계획된 기능:**

- **혼잡도 분석 요청 발행** (Must Have) — 혼잡도가 BUSY(붐빔)로 **상승 전이**될 때만 해당 장소의 areaCode, congestionLevel, populationTime을 RabbitMQ 메시지로 발행. 이전 상태가 이미 BUSY이면 중복 발행하지 않음 (상승 엣지 감지). 상태 저장은 Redis `prev-level:{areaCode}` 사용
- **AI 리포트 조회 API** (Must Have) — AI Service가 Redis(TTL 4시간) + MySQL(비동기)에 저장한 분석 리포트를 areaCode 기반으로 조회. Redis 미스 시 MySQL에서 최근 6시간 이내 리포트만 폴백
- **혼잡/한적 순위 API** (Should Have) — 혼잡도 높은 순/낮은 순 실시간 랭킹 정렬 제공
- **과거 통계 API** (Should Have) — 24시간 시간별·요일별 혼잡도 추이 데이터 제공 (MySQL 이력 데이터 기반)

### 3.3 Map Service (장소 탐색·추천) — 개발 예정

**DB:** MySQL (Spatial Index)

**역할:** "어디로 갈지" — 장소 데이터 관리 + 대체지 추천

**계획된 기능:**

- **대체지 추천** (Must Have) — 프론트엔드가 혼잡 장소의 좌표·카테고리를 전달하면, MySQL Spatial(`ST_Distance_Sphere`)로 반경 2km 이내 유사 카테고리 후보 추출 → 외부 길찾기 API(카카오/네이버)로 각 후보까지 소요시간 병렬 조회 → 소요시간 순 정렬 반환. 혼잡도 필터링은 프론트엔드가 Congestion API 데이터로 처리
- **문화 정보** (Should Have) — 장소 상세에 현재 진행 중인 행사명·시간·장소 노출
- **맛집/놀거리 추천** (Could Have) — 혼잡도 보통 이하 장소 중 외부 맛집 API 평점 순 리스트

**의존:**

- 외부 길찾기 API (카카오/네이버) → 대체지 소요시간 계산

### 3.4 Mobility Service (교통 경로 추천) — 개발 예정

**DB:** 없음 (외부 API만 사용)

**역할:** "어떻게 갈지" — 교통 경로 추천

**계획된 기능:**

- **버스 노선 기반 경로 추천** (Must Have) — 출발지·목적지 입력 → 외부 버스 API로 경로 리스트 조회 → 소요시간 순 정렬

**의존:**

- 외부 버스/경로 API → 노선·경로 데이터

### 3.5 AI Analysis Service (AI 분석) — 개발 예정

**기술 스택:** Python 3.12, FastAPI, aio-pika(RabbitMQ), redis

**인프라:** RabbitMQ에서 분석 요청 수신, 결과를 RabbitMQ 역방향 발행 (저장 책임 없음)

**EDA 설계:**

- **Exchange:** `congestion.events` (topic)
- **수신:** Routing Key `congestion.busy` → Queue: `ai.congestion.analysis`
- **발행:** Routing Key `ai.report` → Queue: `congestion.ai.report` (Congestion Service가 수신하여 DB 저장)
- **배치 처리:** Consumer에서 2초 윈도우로 메시지를 모아 외부 AI API 1회 호출 (JSON 배열). 토큰 절약 + rate limit 회피
- **실패 처리:** 1~2회 재시도 (토큰 미소비 실패만), DLQ 없음

**리포트 저장 (Congestion Service 담당):**

- Redis: `ai-report:{areaCode}` (TTL 4시간) — Congestion Service가 이벤트 수신 시 캐싱
- MySQL: Congestion Service가 RabbitMQ 이벤트를 수신하여 `ai_report` 테이블에 저장 (데이터 소유권 일원화)
- 조회: `GET /api/congestion/{areaCode}/ai-report` (Congestion Service에서 제공)

**AI 프롬프트 컨텍스트:** areaCode(→ 장소명 매핑), congestionLevel, populationTime(→ 시간대·요일 추출)

**계획된 기능:**

- **AI 혼잡 원인 분석** (Could Have) — RabbitMQ에서 areaCode·congestionLevel·populationTime을 수신 → 외부 AI API(미정, 저비용 우선)로 시간대, 요일 등을 종합 분석 → "현재 출근 인파로 인해 평소보다 20% 더 혼잡합니다" 같은 자연어 원인 문장 생성

**확장 계획 — Function Calling:**

- `get_nearby_events(areaCode)`: 공공데이터 축제/행사 API 연동 → 행사가 혼잡 원인인지 분석
- `get_weather(areaCode)`: 기상청 API 연동 → 날씨가 실내 집중 원인인지 분석
- Consumer 내부에 tool 실행 레이어 추가, RabbitMQ/Redis 구조 변경 없음

---

## 4. 인프라 & 배포

### 4.1 Docker Compose 구성

```
Application 클러스터:
  ├── API Gateway (:8080)
  ├── Congestion Analysis Service (:8082)
  ├── Map Service (:8083)
  ├── Mobility Service (:8084)
  └── AI Analysis Service (:8085)

데이터 인프라:
  ├── MySQL 8.0
  ├── Redis 7-alpine
  └── RabbitMQ

Monitoring 클러스터:
  ├── Grafana + Prometheus + cAdvisor
  → Slack 알림 전송

Logging 클러스터:
  ├── Grafana + Grafana Loki

Network: backend-net (bridge)
Volume: mysql-data
```

**기동 순서:** MySQL, Redis, RabbitMQ → 각 서비스 → Gateway

### 4.2 빌드

- **Gradle 8.12** (멀티모듈)
- **Dockerfile**: 멀티스테이지 빌드 (gradle:8.12-jdk21-alpine → eclipse-temurin:21-jre-alpine)

---

## 5. 성능 & 확장성

### 5.1 예상 성능

| 지표 | 값 |
| --- | --- |
| Redis 조회 | ~1-5ms |
| DB 조회 | ~50-100ms |
| API 응답 시간 | <50ms (캐시 히트) |
| 스케줄러 1사이클 | ~3-5s (122개 장소 병렬) |
| 동시 사용자 | 1,000+ (무상태 설계) |

---

## 6. 기능 백로그 (서비스별, MoSCoW 우선순위)

### 6.1 Congestion Analysis Service

| 우선순위 | 기능 | 유저스토리 | 수용 조건 | SP | 상태 |
| --- | --- | --- | --- | --- | --- |
| Must Have | **실시간 혼잡도** | 실시간 혼잡도 | 1. 장소 검색 시 현재 밀집 인원 기반의 혼잡도 등급(원활~매우 혼잡)이 실시간으로 표시된다. 2. 데이터 업데이트 주기를 명시하여 정보의 신선도를 보장한다. | 중 | Backlog |
| Should Have | **과거 통계 그래프** | 과거 통계 그래프 | 1. 24시간 기준의 시간별 혼잡도 추이를 꺾은선 그래프로 제공한다. 2. 요일별 평균 데이터를 선택하여 볼 수 있는 필터 기능을 포함한다. | 하 | Backlog |
| Should Have | **혼잡/한적 순위** | 혼잡/한적 순위 | 1. '실시간 랭킹' 탭에서 혼잡도 높은 순/낮은 순으로 장소 리스트를 정렬하여 보여준다. | 하 | Backlog |

### 6.2 Map Service

| 우선순위 | 기능 | 유저스토리 | 수용 조건 | SP | 상태 |
| --- | --- | --- | --- | --- | --- |
| Must Have | **대체지 추천** | 대체지 추천 | 1. '혼잡' 이상의 구역 선택 시, 인근(반경 2km 내)의 유사 카테고리 중 '원활' 상태인 곳을 상단에 노출한다. 2. 외부 길찾기 API로 소요시간을 조회하여 가기 편한 순으로 정렬한다. | 중 | Backlog |
| Should Have | **문화 정보** | 문화 정보 | 1. 장소 상세 정보에 현재 진행 중인 행사명, 시간, 장소 정보를 텍스트와 이미지로 노출한다. | 하 | Backlog |
| Could Have | **맛집/놀거리 추천** | 맛집/놀거리 추천 | 1. 혼잡도 '보통' 이하인 장소들 중 외부 맛집 API 평점이 높은 순으로 리스트를 제공한다. | 중 | Backlog |
| Won't Have | **목적별 필터링** | 목적별 필터링 | 1. 메인 화면 상단에 '카공', '산책', '쇼핑' 등 태그 버튼을 배치하여 지도 마커를 필터링한다. | 하 | Backlog |

### 6.3 Mobility Service

| 우선순위 | 기능 | 유저스토리 | 수용 조건 | SP | 상태 |
| --- | --- | --- | --- | --- | --- |
| Must Have | **주요 버스 노선 기반 경로** | 주요 버스 노선 기반 경로 | 1. 출발지와 목적지 입력 시 주요 버스 노선을 포함한 경로 리스트가 출력된다. 2. 소요시간 순으로 정렬되어 가장 빠른 경로가 추천 마크와 함께 표시된다. | 상 | Backlog |
| Won't Have | **교통 정보** | 교통 정보 | 1. 지도 내에 공공 데이터(TOPIS 등)와 연동된 실시간 도로 소통 정보(원활/서행/정체)를 표시한다. | 하 | Backlog |

### 6.4 AI Analysis Service

| 우선순위 | 기능 | 유저스토리 | 수용 조건 | SP | 상태 |
| --- | --- | --- | --- | --- | --- |
| Must Have | **AI 혼잡 원인 분석** | AI 혼잡 원인 분석 | 1. BUSY 상승 전이 시 RabbitMQ 이벤트 발행 → 외부 AI API로 자연어 원인 문장 생성. 2. 배치 처리(2초 윈도우)로 토큰 절약. 3. RabbitMQ 역방향 발행으로 Congestion Service에 저장 위임 (Redis + MySQL). | 상 | Backlog |
| Could Have | **AI Function Calling 확장** | AI Function Calling | 1. 축제/행사 API, 기상청 API를 AI가 능동적으로 호출하여 근거 있는 원인 분석을 제공한다. | 중 | Backlog |

### 6.5 크로스 서비스

| 우선순위 | 기능 | 유저스토리 | 수용 조건 | SP | 상태 |
| --- | --- | --- | --- | --- | --- |
| Won't Have | **재난 문자** | 재난 문자 | 1. GPS상 위험 구역 진입 시 Push 알림을 발송하며, 알림 클릭 시 대피 경로 안내 화면으로 연결한다. | 상 | Backlog |

---

## 7. 로드맵

### Phase 1 — 핵심 인프라 (현재) ✅

- 멀티모듈 아키텍처
- 혼잡도 서비스 (서울시 API 연동) — **실시간 혼잡도** 백엔드 완료
- API Gateway 라우팅
- Observability (Grafana + Prometheus + Loki)

### Phase 2 — Must Have 기능 구현

- **RabbitMQ 연동**: BUSY 상승 엣지 감지 시 AI 분석 요청 발행 (Congestion → RabbitMQ → AI)
- **AI 혼잡 원인 분석**: 외부 AI API 연동, 배치 처리(2초 윈도우), RabbitMQ 역방향 이벤트로 Congestion Service에 저장 위임 (AI Service)
- **AI 리포트 조회 API**: Redis → MySQL 폴백(6시간 이내) (Congestion Service)
- **대체지 추천**: MySQL Spatial 후보 추출 → 외부 길찾기 API 소요시간 순 (Map Service)
- **버스 노선 기반 경로 추천**: 외부 버스 API + 소요시간 순 정렬 (Mobility Service)

### Phase 3 — Should Have 기능 구현

- **과거 통계 그래프**: 시간별·요일별 혼잡도 추이 API (Congestion Analysis Service)
- **혼잡/한적 순위**: 실시간 랭킹 정렬 API (Congestion Analysis Service)
- **문화 정보**: 장소별 행사 정보 연동 (Map Service)

### Phase 4 — Could Have 기능 구현

- **AI Function Calling 확장**: 축제/행사 API, 기상청 API 연동으로 근거 있는 원인 분석 (AI Service)
- **맛집/놀거리 추천**: 외부 맛집 API 연동, 혼잡도 기반 필터링 (Map Service)

### Phase 5 — 인프라 안정화 & 프로덕션 준비

- **서킷브레이커 / 타임아웃 / 폴백**: 서비스 간 통신 장애 대응 (Resilience4j 등)
- **스케줄러 분산 락**: 수평 확장 시 중복 실행 방지 (ShedLock + Redis)
- **CORS 설정**: 프론트엔드(Vercel) 도메인 허용
- **Rate Limiting**: API Gateway에 요청 제한 설정
