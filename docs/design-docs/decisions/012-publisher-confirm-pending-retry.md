# 012. 이벤트 발행에 Publisher Confirm 을 켜고, 실패분은 다음 주기에 재발행한다

- 날짜: 2026-10-16
- 상태: 채택

## 배경
`CongestionEventPublisher` 는 `convertAndSend` 만 하는 fire-and-forget 이었다. 브로커가 순간 내려가면 예외는 스케줄러가 로그만 남기고 삼키고, 연결은 살아 있어도 브로커가 받지 못한 메시지는 아무도 모른다. BUSY 이벤트는 상승 엣지에서만 발행하므로(001) 한 번 잃으면 다음 주기에도 다시 감지되지 않는다. DLQ(002)는 소비 쪽 유실만 막는다.

## 결정
- `publisher-confirm-type: correlated`, `publisher-returns: true`, `template.mandatory: true`
- 발행마다 `CorrelationData` 로 confirm 을 최대 5초 동기 대기한다. ack 이고 반송이 없을 때만 성공
- nack, 타임아웃, 반송(라우팅 불가), 연결 예외는 메모리 대기열(`ConcurrentLinkedQueue`)에 보관한다
- `CongestionScheduler.fetchAndSave` 가 주기 시작 시 `republishPending()` 을 먼저 부른다. 서울 API 장애로 수집이 실패해도 재발행은 돈다. 대기열 앞에서부터 보내고, 하나라도 실패하면 브로커가 아직 장애라 보고 그 주기의 재발행을 멈춘다 (순서 유지, 장애 중 매 주기 전체 재시도로 스케줄러가 막히는 것 방지)
- `spring.rabbitmq.connection-timeout: 5s`. 기본 60초면 브로커 불통 시 발행 1건이 스케줄러를 1분 막는다

## 근거
- 측정 (로컬 RabbitMQ 컨테이너, 100건을 100ms 간격 발행, 3초 후 broker stop, 4초 뒤 start):
  - Before (fire-and-forget): 큐 적재 31건, 유실 69건. 69건 모두 연결 예외로 스케줄러가 삼킨 건
  - After (confirm + 대기열): 발행 중 71건이 대기열로, 복구 후 재발행해서 큐 적재 100건, 유실 0건. 재발행 중단 로직과 연결 타임아웃 5초를 넣은 최종 코드로 다시 측정: 대기 63건, 유실 0건
- 측정 코드는 Docker 가 필요해 CI 에 넣지 않았다. 재현 절차는 위와 같고, 임시 JUnit 으로 `CachingConnectionFactory`(5673) 에 붙여 `admin.getQueueInfo` 로 건수를 셌다
- 버린 대안
  - Transactional Outbox: DB 에 이벤트를 먼저 쓰고 릴레이가 발행. 재시작에도 안전하지만 테이블·마이그레이션·릴레이 스케줄러가 늘어난다. 상승 엣지 이벤트는 주기당 수 건이라 메모리 대기열로 충분하다고 봤다
  - 상태 되돌리기: 실패 시 이전 상태를 되돌려 다음 주기에 재감지. 이전 상태를 응답용 Redis 캐시에서 읽기 때문에 캐시까지 건드려야 해서 버렸다
  - 비동기 confirm 콜백: 콜백에서 재시도하면 스레드와 순서 관리가 늘어난다. 주기당 수 건이라 동기 대기 비용이 작다

## 결과/영향
- 남은 한계: 브로커 장애 중 congestion 이 재시작되면 메모리 대기열이 사라진다. 막아야 하면 Outbox 로 간다 (`tech-debt.md`)
- 재발행은 다음 주기(5분)라 브로커 복구 후 AI 분석이 최대 한 주기 늦는다
- 브로커 장애 중 새로 감지된 이벤트는 건당 최대 약 5초(연결 타임아웃 또는 confirm 타임아웃)를 쓴다. 상승 엣지라 주기당 수 건이다
- 장애가 길면 대기 이벤트가 몇 시간 뒤 옛 `populationTime` 으로 발행될 수 있다. AI 는 그 시점 기준으로 분석한다. 문제가 되면 오래된 이벤트를 버리는 기준을 넣는다
- 같은 이벤트가 두 번 발행될 수 있다(ack 유실 후 재발행). 소비 쪽 분산락(004, 013)과 저장 UNIQUE(005)가 중복을 흡수한다

## 관련
- `001-rabbitmq-eda.md`, `002-dlq-message-loss.md`, `004-redis-lock-llm-dedup.md`, `005-ai-report-idempotent-insert.md`
