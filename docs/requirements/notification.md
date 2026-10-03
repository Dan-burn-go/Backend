# 혼잡 알림 구독 (service-congestion / notification)

출처: 코드. PRD 에는 이 기능의 명세가 없다 (6.5 의 재난 문자는 `Won't Have` 로 다른 기능이다).

`service-congestion` 안의 독립 하위 도메인으로, 자체 controller·service·repository·스케줄러를 가진다 (`ARCHITECTURE.md`).

| ID | 성립해야 할 것 | 검증 | 출처 | 상태 |
|---|---|---|---|---|
| NT-1 | 장소를 구독하면 구독이 생성된다 | `SubscriptionServiceTest.newSubscription` | 코드 | 자동 |
| NT-2 | 같은 구독을 다시 요청하면 갱신한다 | `SubscriptionServiceTest.renewSubscription` | 코드 | 자동 |
| NT-3 | 구독을 해지할 수 있다 | `SubscriptionServiceTest.unsubscribe` | 코드 | 자동 |
| NT-4 | 없는 장소 구독은 거부한다 | `SubscriptionServiceTest.areaNotFound` | 코드 | 자동 |
| NT-5 | BUSY 가 아니면 알림을 보내지 않는다 | `SubscriptionServiceTest.notBusy` | 코드 | 자동 |
| NT-6 | 최근에 보낸 구독에는 중복 발송하지 않는다 | `SubscriptionServiceTest.recentlyFired` | 코드 | 자동 |
| NT-7 | 저장 경합(동시 구독)에서 충돌을 처리한다 | `SubscriptionServiceTest.conflictOnSave` | 코드 | 자동 |
| NT-8 | 만료된 구독을 주기적으로 삭제한다 | `ExpiredSubscriptionCleanupTest.callsDeleteByExpiresAtBefore` | 코드 | 자동 |
| NT-9 | 삭제 대상이 0건이어도 정상 처리하고, 예외는 흡수해 다음 주기를 지킨다 | `ExpiredSubscriptionCleanupTest.zeroDeletedIsOk`, `ExpiredSubscriptionCleanupTest.exceptionIsSwallowed` | 코드 | 자동 |
| NT-10 | VAPID 키가 비었거나 잘못되면 미설정으로 판정한다 | `WebPushSenderTest.notConfiguredWhenKeysBlank`, `WebPushSenderTest.notConfiguredWhenKeysInvalid` | 코드 | 자동 |
| NT-11 | 초기화되지 않은 상태의 발송 요청은 재시도로 응답한다 | `WebPushSenderTest.returnsRetryWhenNotInitialized` | 코드 | 자동 |
| NT-12 | 헬스 인디케이터가 설정 여부에 따라 UP/DOWN 을 보고한다 | `WebPushHealthIndicatorTest.upWhenConfigured`, `WebPushHealthIndicatorTest.downWhenNotConfigured` | 코드 | 자동 |

Web Push 는 키 미설정 시 기능을 끄고 헬스에 DOWN 으로 드러낸다. 조용히 실패하지 않는 쪽을 택한 구조다.
