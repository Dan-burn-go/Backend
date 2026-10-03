# 003. 서비스 간 동기 호출에 Circuit Breaker + TimeLimiter + Redis 캐시 폴백을 끼운다

- 날짜: 2026-06-12
- 상태: 채택

## 배경
`service-map` 이 대체지 추천에 혼잡도가 필요해 `service-congestion` 을 동기 호출한다. 이 저장소에서 유일한 서비스 간 동기 호출이고, 피호출 서비스나 그 뒤의 외부 API 가 느려지면 호출자 스레드가 함께 묶인다.

## 결정
Resilience4j Circuit Breaker 와 TimeLimiter 를 적용하고, 열렸을 때는 Redis 캐시 값으로 폴백한다.

- 실패율 50%, 슬라이딩 윈도우 10, Open 유지 30초
- TimeLimiter 3초
- 폴백: Redis 에 남은 직전 혼잡도

ODsay 연동(`service-mobility`)의 서킷 폴백 상태코드는 503 이 아니라 500 으로 맞췄다(#371). 503 은 호출자 재시도를 유도해 열린 서킷에 다시 부하를 준다.

## 근거
- 적용 전 외부 API 지연 시 응답이 3초까지 늘어났고, 적용 후 열린 서킷에서 6ms fail-fast 로 끊긴다
- 혼잡도는 5분 주기로 갱신되는 값이라 캐시된 직전 값이 빈 응답보다 쓸모 있다
- 서킷 상태는 대시보드로 관측한다 (`grafana/dashboards/circuit-breaker-dashboard.json`)

## 결과/영향
- 서킷이 열린 동안 사용자는 최신이 아닌 혼잡도를 볼 수 있다. 빈 응답보다 낫다고 판단했다
- 폴백 경로가 Redis 가용성에 의존한다. Redis 가 죽으면 폴백도 없다

## 관련
- 커밋 `f5be24c` (#364), `c357fce` (#371), `ee87934` (#356 서킷 모니터링), `61f843e` (#341 Retry·fallback 예외 처리)
