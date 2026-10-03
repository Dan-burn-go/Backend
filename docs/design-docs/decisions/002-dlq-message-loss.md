# 002. 외부 API Rate Limit 실패를 DLQ 로 분리해 메시지 유실을 막는다

- 날짜: 2026-05 (블로그 기록 기준)
- 상태: 채택

## 배경
AI 분석 파이프라인에서 외부 API 가 429 를 반환하면 메시지가 유실됐다. 쿼터 회복에 수 분이 걸려 즉시 재시도로는 해결되지 않고, 재시도가 정상 메시지 처리까지 막았다.

## 결정
RabbitMQ DLQ 로 실패 경로와 정상 경로를 분리한다. 실패 메시지는 DLQ 로 보내 나중에 복구하고, 정상 경로는 막지 않는다.

메시지별 재시도 한도는 헤더 `x-attempt-count` 로 누적 추적하고, republish 3회를 넘기면 ack 로 영구 폐기하면서 ERROR 로그를 남긴다. 한도가 없으면 회복되지 않는 메시지가 무한 순환한다.

## 근거
- 단순 재시도는 쿼터 회복 시간(수 분)을 기다리는 동안 정상 처리를 함께 멈춘다
- 배포 후 메시지 유실 0건, DLQ 경유 복구를 확인했다
- 부하 시나리오는 `load-test/k6/dlq.js`, `load-test/DLQ-TEST.md` 로 재현한다

## 결과/영향
- 실패 메시지가 즉시 처리되지 않고 지연된다. 분석 리포트가 늦게 도착할 수 있다
- DLQ 적체 자체가 관측 대상이 됐다 (`grafana/dashboards/dlq-dashboard.json`)

## 관련
- `001-rabbitmq-eda.md`, `006-llm-429-backoff.md`
- [블로그: DLQ 도입으로 API Rate Limit 환경에서 메시지 유실 제로 달성](https://velog.io/@kim138762/DLQ-%EB%8F%84%EC%9E%85%EC%9C%BC%EB%A1%9C-API-Rate-Limit-%ED%99%98%EA%B2%BD%EC%97%90%EC%84%9C-%EB%A9%94%EC%8B%9C%EC%A7%80-%EC%9C%A0%EC%8B%A4-%EC%A0%9C%EB%A1%9C-%EB%8B%AC%EC%84%B1)
