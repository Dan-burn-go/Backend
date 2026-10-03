# 001. Java 서비스와 Python AI 서비스를 RabbitMQ 비동기 EDA 로 분리한다

- 날짜: 2026-04 (도입 시점, 블로그 기록 기준)
- 상태: 채택

## 배경
혼잡도 수집은 5분 주기로 122개 장소를 한 번에 처리한다. 여기에 AI 분석을 붙이자 수집 버스트가 그대로 외부 AI API 호출 버스트가 되어 호출 제한과 충돌했다.

## 결정
Java(수집·저장)와 Python(AI 분석)을 별 서비스로 분리하고, 둘 사이를 RabbitMQ 양방향 이벤트로만 연결한다. 동기 호출은 두지 않는다.

- `congestion.events` topic exchange
- 발행: routing key `congestion.busy` → 큐 `ai.congestion.analysis`
- 역방향: routing key `ai.report` → 큐 `congestion.ai.report`
- 저장은 `service-congestion` 이 단독으로 한다. `service-ai` 는 DB 에 쓰지 않는다

발행 조건은 상승 엣지만이다. 이전 상태 != BUSY && 현재 == BUSY 일 때만 발행한다. 수집 주기마다 발행하면 같은 혼잡 상태를 반복 분석해 호출 제한을 다시 만난다.

## 근거
- 수집 버스트와 AI 호출 제한이 실제로 충돌한 사례가 도입 동기였다
- 큐가 완충 역할을 하면서 호출 제한 안에서 안정적으로 처리된다
- AI 서비스 장애가 수집 파이프라인으로 전파되지 않는다 (장애 격리)
- 저장을 한 서비스로 모으면 Redis 캐시와 MySQL 이력의 정합성을 한 곳에서 보장한다

버린 대안: 기록이 남아 있지 않다. 동기 REST 호출을 쓰다 전환한 것으로 읽히지만 당시 비교한 다른 선택지(예: Kafka)의 흔적은 저장소와 블로그 글에 없다.

## 결과/영향
- 서비스 경계가 언어 경계와 일치한다. `service-ai` 만 Python 이고 Gradle 밖에 있다
- 분석 결과를 즉시 응답할 수 없다. 조회는 캐시·DB 에 적재된 뒤에만 가능하다
- 큐 적체와 실패 메시지 처리가 새 문제로 올라왔다 → `002-dlq-message-loss.md`

## 관련
- `service-ai/CLAUDE.md`, `ARCHITECTURE.md`
- [블로그: RabbitMQ 기반 비동기 EDA 구조를 이용한 AI분석 기능 도입](https://velog.io/@kim138762/RabbitMQ-%EA%B8%B0%EB%B0%98-%EB%B9%84%EB%8F%99%EA%B8%B0-EDA-%EA%B5%AC%EC%A1%B0%EB%A5%BC-%EC%9D%B4%EC%9A%A9%ED%95%9C-AI%EB%B6%84%EC%84%9D-%EA%B8%B0%EB%8A%A5-%EB%8F%84%EC%9E%85)
