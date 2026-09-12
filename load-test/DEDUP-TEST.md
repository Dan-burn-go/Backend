# Redis 분산락 Dedup 검증 (부하테스트)

`service-ai`의 Redis 분산락이 **At-Least-Once 재전달로 인한 중복 이벤트를 앞단에서 걸러 LLM 호출을 딱 1번만** 나가게 하는지 검증한다.

## 핵심 가설
> 같은 `(area_code, population_time)` 이벤트가 아무리 많이 중복 유입돼도 **실제 LLM 호출은 1회**.

- 판정 지표: **WireMock에 기록된 실제 LLM HTTP 호출 수** (외부 실측, 불가위조)
- 합격: 유입 N건 → LLM 호출 **1건** (`1 − 1/N` 절감)

## 왜 멀티서버인가
분산락의 존재 이유는 **여러 인스턴스가 같은 이벤트를 동시에 소비**할 때 막기 위함이다.
- 단일서버이면 자바 `synchronized`/인메모리 Set으로도 통과 → "분산락 성과" 증명 불가
- **2개 서버**에 중복이 분산 유입돼도 LLM 1회면 → 로컬 자료구조로는 불가능, **공유 Redis만** 가능 = 증명

## 구성
- `docker-compose.loadtest.yml`: WireMock(LLM 목킹) + `service-ai-1` / `service-ai-2`
- `BATCH_MAX_SIZE=1`: 이벤트 1개 = LLM 호출 1회로 만들어 **WireMock 호출 수 = 고유 이벤트 수** 1:1 대응
  (배치가 여러 이벤트를 묶으면 dedup 없이도 호출 1회라 효과가 안 드러남)
- 락 선점 시점: 배치 flush(LLM 호출 직전). 멀티서버이라 배치/로컬 캐시로는 못 막으므로 Redis가 주체임이 증명됨

## 실행

### 1. 스택 기동
```bash
docker compose -f docker-compose.yml -f docker-compose.loadtest.yml up -d \
  redis rabbitmq observability wiremock service-ai-1 service-ai-2
```

### 2. Redis 초기화 (필수 — 이전 실행 멱등성 키 제거)
```bash
docker compose exec redis redis-cli FLUSHDB
```
> 안 하면 이전 마커가 남아 항상 LLM 0회로 보일 수 있음. (또는 매 실행 `-e POP_TIME` 을 다르게)

### 3. k6 동시 버스트 실행
```bash
k6 run load-test/k6/dedup.js
# 유입 수/키 조정:
k6 run -e N=50 -e POP_TIME="2026-07-06 16:00" load-test/k6/dedup.js
```
k6 `teardown`이 WireMock Admin API로 실제 호출 수를 조회해 **PASS/FAIL**을 출력한다.

### 4. 수동 교차 검증 (WireMock Admin)
```bash
curl -s -X POST http://localhost:8089/__admin/requests/count \
  -H 'Content-Type: application/json' \
  -d '{"method":"POST","urlPathPattern":".*/chat/completions"}'
# → {"count": 1}  이면 통과
```

### 5. Grafana 확인
- http://localhost:3000 → 대시보드 **"AI Redis 분산락 Dedup"**
- 패널: `선점 성공(=1)` vs `중복 스킵(=N−1)`, `서버별 처리 분산`(2개 서버), 스킵 스파이크 시계열
- 로그 교차검증(Explore → Loki):
  - `{app="service-ai"} |= "배치 처리 시작"` → **1건** (락 획득)
  - `{app="service-ai"} |= "중복 요청"` → **N−1건** (스킵)
  - `{app="service-ai"} |= "중복 요청"` 를 `server` 별로 보면 두 서버에 분산

## 합격 판정 체크리스트
- [ ] WireMock `.*/chat/completions` 호출 수 = **1**
- [ ] 두 서버(`service-ai-1`, `service-ai-2`) 로그를 합쳐 `배치 처리 시작` 1건 / `중복 요청` N−1건
- [ ] Grafana 서버별 분산 파이에 **2개 서버** 모두 표시 (중복이 분산 유입됐다는 증거)

## (선택) 단일서버 대조 실험
"분산락이 진짜 필요한가"를 보여주려면:
```bash
# 단일서버: service-ai-1 만 기동 → 여기선 로컬로도 통과할 수 있음
docker compose -f docker-compose.yml -f docker-compose.loadtest.yml up -d \
  redis rabbitmq observability wiremock service-ai-1
```
멀티서버 결과(LLM=1)와 나란히 캡처하면 "멀티서버에서도 1회 = Redis 분산락 덕분"이 명확해진다.

## 참고
- WireMock 스텁: `load-test/wiremock/mappings/llm-chat-completions.json` — `{"results": []}` 반환(파싱 안전, 툴콜 없음 → 1회 왕복). 응답 내용은 검증 대상이 아니라 **호출 수만** 센다.
- `/test/publish` 는 `area_code`, `population_time`, `area_name`, `congestion_level` 쿼리 파라미터를 받는다.
