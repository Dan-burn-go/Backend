# 부하테스트 (Load Test)

게이트웨이를 통한 동기 HTTP 경로의 **회귀 감지용 기준선**을 측정한다.
관련 이슈: [#333](https://github.com/Dan-burn-go/Backend/issues/333)

> 로컬 단일 머신 측정치는 **상대치(회귀 감지)** 로만 사용한다.
> SLA 판정·캐파시티 결정은 dev/staging 재측정 후 별도 이슈에서 진행한다.

## 대상 (issue #333)

| 메서드 | 경로 | k6 tag | 부하 특성 |
|---|---|---|---|
| GET | `/api/congestion` | `list` | 122개 목록 — 가장 무거움 |
| GET | `/api/congestion/{areaCode}` | `detail` | 단일 조회 — 캐시 hit 기대 |
| GET | `/api/congestion/analysis/hourly/{areaCode}?days=7` | `hourly` | 시간별 집계 쿼리 |
| GET | `/api/congestion/analysis/ranking/busiest?limit=10` | `ranking` | 정렬+집계 |

외부 의존성이 큰 경로(`/api/mobility/route`, `/api/map/*`, `*/ai-report`)와 비동기 service-ai 는 범위에서 제외.

## 사전 준비

1. k6 설치 (macOS): `brew install k6`
2. 인프라 기동: `docker compose -f docker-compose.local.yml up -d` (MySQL, Redis)
3. 게이트웨이 + service-congestion 기동
   ```bash
   SPRING_PROFILES_ACTIVE=local ./gradlew :service-gateway:bootRun
   SPRING_PROFILES_ACTIVE=local ./gradlew :service-congestion:bootRun
   ```
   > docker compose 로 띄운 게이트웨이가 떠 있으면 8080 포트가 충돌하므로 둘 중 하나만 실행한다.
   > Gradle 로 띄우는 경우 rate limit 해제는 같은 명령에 환경변수를 붙인다 (아래 "Rate Limiter 주의" 참고):
   > ```bash
   > GATEWAY_RATE_REPLENISH=100000 GATEWAY_RATE_BURST=200000 \
   >   SPRING_PROFILES_ACTIVE=local ./gradlew :service-gateway:bootRun
   > ```
4. 헬스 확인: `curl http://localhost:8080/api/congestion` 이 200 이고 `data` 배열이 비어있지 않은지 확인.

> 첫 실행 시 `https://jslib.k6.io` 의 summary 라이브러리를 받아오므로 인터넷 연결이 필요하다.

## 실행

**프로젝트 루트에서** 실행한다 (결과가 `load-test/results/` 에 저장됨):

```bash
# 로컬
k6 run load-test/k6/ramp-up.js

# 다른 환경 (BASE_URL 덮어쓰기)
k6 run -e BASE_URL=https://dev.example.com load-test/k6/ramp-up.js
```

## 시나리오

VU 1 → 50 선형 증가:

```
stages: [
  { duration: '30s', target: 5 },  // 워밍업
  { duration: '5m',  target: 50 }, // 선형 증가
  { duration: '1m',  target: 0 },  // cooldown
]
```

## VU 수준 결정 가이드 (DAU 기준)

| DAU 구간 | 권장 VU | 환경 |
|---|---|---|
| **DAU 1만 미만** | **VU 50** (현재 채택) | 로컬 회귀 감지용 베이스라인 |
| DAU 1만 ~ 10만 | VU 100 ~ 300 | 로컬 또는 dev |
| DAU 10만 이상 | VU 500+ | dev / staging 필수 |

VU 50 ≈ 150~500 RPS (평균 응답 100~300ms 가정). VU 조정은 `ramp-up.js` 의 `stages` 에서 `target` 값을 바꾼다.

## 임계값 (thresholds)

| 메트릭 | 기준 |
|---|---|
| `http_req_failed` | 에러율 < 1% |
| `http_req_duration` (전체) | p95 < 500ms |
| `endpoint:list` | p95 < 800ms |
| `endpoint:detail` | p95 < 300ms |
| `endpoint:hourly` / `endpoint:ranking` | p95 < 800ms |

기준 초과 시 k6 종료 코드가 0 이 아니다. 첫 측정에서는 이 값들이 **임시 가드레일**이며, 실측 후 기준선에 맞게 조정한다.

## 결과 / 기준선

실행 후 요약은 stdout 과 `load-test/results/summary.json` 에 저장된다.
측정할 때마다 아래 표를 갱신해 회귀 추적에 사용한다.

| 측정일 | 환경 | 최대 RPS | p95 (list) | p95 (detail) | p95 (hourly) | p95 (ranking) | 에러율 |
|---|---|---|---|---|---|---|---|
| 2026-05-28 | local (rate limit 해제) | ~97 | 27.6ms | 8.5ms | 8.8ms | 9.95ms | 0% |

> 2026-05-28 측정: VU 1→50 구간 내내 안정적(37,869 req, checks 100%, interrupted 0). 로컬 단일 머신이라 절대치보다 **회귀 기준선**으로만 사용.

## ⚠️ 게이트웨이 Rate Limiter 주의

게이트웨이는 모든 라우트에 **IP 기반 RequestRateLimiter**(`replenishRate 10, burstCapacity 20`)를 default-filter로 적용한다. KeyResolver가 `getRemoteAddress()` 기반이라 단일 클라이언트(k6)는 **초당 10요청**에서 막혀 대부분 429를 받는다 (X-Forwarded-For 헤더로는 우회 불가).

부하테스트로 **다운스트림 용량**을 보려면 rate limit을 환경변수로 풀어야 한다 (기본값은 운영 설정 그대로 보존):

```bash
GATEWAY_RATE_REPLENISH=100000 GATEWAY_RATE_BURST=200000 \
  infisical run --silent -- docker compose up -d --no-deps --force-recreate service-gateway
```

rate limit을 켠 채로 측정하면 "IP당 10 RPS 보호가 작동한다"는 결과만 나오고 다운스트림 부하는 측정되지 않는다.
