# AI 혼잡 원인 분석 (service-ai + service-congestion)

출처: `docs/product-specs/PRD.md` 3.5, 6.4

PRD 6.4 는 배치 윈도우를 2초로 적었으나 코드 기본값은 5초다 (`service-ai/app/config.py:52`). 기준은 코드 값을 따른다.

| ID | 성립해야 할 것 | 검증 | 출처 | 상태 |
|---|---|---|---|---|
| AI-1 | BUSY 상승 전이에서만 이벤트를 발행한다. 이미 BUSY 면 발행하지 않는다 | `CongestionStateTrackerTest.risingEdge`, `CongestionStateTrackerTest.alreadyBusy` | PRD 3.2, 결정 `../design-docs/decisions/001-rabbitmq-eda.md` | 자동 |
| AI-2 | 첫 관측에서 BUSY 면 발행한다 | `CongestionStateTrackerTest.firstDetection` | 코드 | 자동 |
| AI-3 | BUSY 가 없으면 아무것도 발행하지 않는다 | `CongestionStateTrackerTest.noBusy` | 코드 | 자동 |
| AI-4 | 이전 상태 Redis 미스 시 DB 로 폴백해 판정한다 | `CongestionStateTrackerTest.redisMissFallbackToDb` | 코드 | 자동 |
| AI-5 | busy 이벤트와 anomaly 이벤트를 각각 발행한다 | `CongestionEventPublisherTest.publishBusy`, `CongestionEventPublisherTest.publishAnomaly` | 결정 `../design-docs/decisions/008-anomaly-reanalysis.md` | 자동 |
| AI-6 | ratio 또는 delta 임계를 넘으면 anomaly 로 판정한다 | `AnomalyDetectorTest.ratioExceedsThreshold`, `AnomalyDetectorTest.deltaExceedsThreshold` | 결정 008 | 자동 |
| AI-7 | 두 임계 모두 미달이면 anomaly 가 아니다 | `AnomalyDetectorTest.ratioAndDeltaBelowThreshold` | 결정 008 | 자동 |
| AI-8 | anomaly TTL 설정이 0 이하이면 기본값으로 되돌린다 | `AnomalyDetectorTest.armedTtlFallsBackToDefaultWhenNonPositive` | 결정 008 | 자동 |
| AI-9 | 분석 결과 저장은 멱등하다. 중복 메시지가 와도 실패하지 않는다 | `AiReportEventConsumerTest.duplicateIgnored` | 결정 `../design-docs/decisions/005-ai-report-idempotent-insert.md` | 자동 |
| AI-10 | 분석 결과를 Redis 캐싱과 MySQL 저장에 함께 반영한다 | `AiReportEventConsumerTest.insertedAndCached` | PRD 3.2 | 자동 |
| AI-11 | AI 리포트 조회는 없는 areaCode 에 404 를 준다 | `AireportControllerTest.getLatestAiReport_notFound` | PRD 3.2 | 자동 |
| AI-12 | 같은 장소·시점의 중복 요청은 LLM 을 두 번 호출하지 않는다 | `service-ai/tests/test_dedup.py::test_duplicate_redelivery_skips_llm` | 결정 `../design-docs/decisions/004-redis-lock-llm-dedup.md` | 자동 |
| AI-13 | 한 배치 안의 중복도 제거한다 | `service-ai/tests/test_dedup.py::test_duplicate_within_single_batch_deduped` | 결정 004 | 자동 |
| AI-14 | 분산락 키가 DB 의 유니크 키와 같은 기준이다 | `service-ai/tests/test_dedup.py::test_key_matches_db_unique_area_time` | 결정 004, 005 | 자동 |
| AI-15 | Redis 장애 시 중복 억제를 포기하고 분석은 계속한다 (fail-open) | `service-ai/tests/test_dedup.py::test_redis_failure_fails_open` | 결정 004 | 자동 |
| AI-16 | 성공한 배치는 모든 메시지를 ack 한다 | `service-ai/tests/test_batch_ack_nack.py::test_success_path_acks_all_messages` | 결정 `../design-docs/decisions/002-dlq-message-loss.md` | 자동 |
| AI-17 | 재시도 가능·불가능 오류와 알 수 없는 예외를 모두 DLQ 로 보낸다 | `service-ai/tests/test_batch_ack_nack.py::test_retriable_error_nacks_to_dlq`, `service-ai/tests/test_batch_ack_nack.py::test_non_retriable_error_nacks_to_dlq`, `service-ai/tests/test_batch_ack_nack.py::test_unknown_exception_nacks_to_dlq` | 결정 002 | 자동 |
| AI-18 | 발행 실패 시 성공한 배치도 DLQ 로 보내 유실을 막는다 | `service-ai/tests/test_batch_ack_nack.py::test_publisher_failure_dlqs_successful_batch` | 결정 002 | 자동 |
| AI-19 | DLQ 재시도 횟수를 헤더로 누적하고 한도 초과 시 영구 폐기한다 | `service-ai/tests/test_dlq_attempt.py::test_existing_attempt_count_increments`, `service-ai/tests/test_dlq_attempt.py::test_attempt_at_threshold_triggers_permanent_discard` | 결정 002 | 자동 |
| AI-20 | 잘못된 attempt 헤더는 0 으로 취급한다 | `service-ai/tests/test_dlq_attempt.py::test_invalid_attempt_header_treated_as_zero` | 코드 | 자동 |
| AI-21 | republish 실패 시 ack 하지 않는다 | `service-ai/tests/test_dlq_attempt.py::test_publish_failure_returns_false_without_ack` | 결정 002 | 자동 |
| AI-22 | 토큰 쿼터 429 는 재시도하고 큐 초과 429 는 재시도하지 않는다 | `service-ai/tests/test_openai_client_429.py::test_token_quota_exceeded_is_retriable_with_retry_after`, `service-ai/tests/test_openai_client_429.py::test_queue_exceeded_is_non_retriable` | 결정 `../design-docs/decisions/006-llm-429-backoff.md` | 자동 |
| AI-23 | Retry-After 가 없으면 reset 헤더로, 둘 다 없으면 기본 대기로 떨어진다 | `service-ai/tests/test_openai_client_429.py::test_token_quota_exceeded_fallbacks_to_reset_header`, `service-ai/tests/test_openai_client_429.py::test_unknown_429_is_retriable_with_default_wait` | 결정 006 | 자동 |
| AI-24 | 검색 도구 입력에서 날짜 토큰을 제거하고, 다 지워지면 원문으로 되돌린다 | `service-ai/tests/test_mcp_server_sanitize.py::test_sanitize_strips_date_tokens`, `service-ai/tests/test_mcp_server_sanitize.py::test_sanitize_empty_after_strip_falls_back_to_original` | 결정 `../design-docs/decisions/007-mcp-function-calling.md` | 자동 |

| ID | 확인할 것 | 방법 | 상태 |
|---|---|---|---|
| AI-M1 | 생성된 원인 문장이 실제 상황과 맞다 | Grafana Log Stream 의 `[Analysis]` 로그를 드래그해 외부 LLM 으로 채점 | 수동 |
| AI-M2 | TPD soft limit 80% 도달 시 도구 미주입으로 떨어진다 | 운영 로그에서 tool_called=false 전환 확인 | 수동 |

AI-M1 은 서비스 내부 검증 시스템이 없어 수동이다 (결정 007).
