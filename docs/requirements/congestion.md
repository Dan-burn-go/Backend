# 혼잡도 수집·제공 (service-congestion)

출처: `docs/product-specs/PRD.md` 3.2, 6.1

PRD 6.1 은 세 기능을 모두 `Backlog` 로 적어 두었지만 실시간 혼잡도·과거 통계·랭킹은 이미 구현돼 테스트가 있다. PRD 상태 칸이 낡았다 (`docs/exec-plans/tech-debt.md`).

| ID | 성립해야 할 것 | 검증 | 출처 | 상태 |
|---|---|---|---|---|
| CG-1 | 서울시 API 에서 122개 장소 혼잡도를 수집해 저장한다 | `CongestionSchedulerTest.fetchAndSave_success` | PRD 3.2 | 자동 |
| CG-2 | 수집 중 외부 API 예외가 나도 스케줄러가 죽지 않는다 | `CongestionSchedulerTest.fetchAndSave_apiException` | PRD 3.2 | 자동 |
| CG-3 | 빈 응답과 forecast 누락을 저장 단계에서 걸러낸다 | `CongestionSchedulerTest.fetchAndSave_emptyResponse`, `CongestionSchedulerTest.fetchAndSave_nullForecasts` | PRD 3.2 | 자동 |
| CG-4 | 혼잡도를 Redis 에 캐싱하고 다건 조회를 파이프라인으로 처리한다 | `CongestionRedisRepositoryImplTest.multiGet` | PRD 3.2 | 자동 |
| CG-5 | Redis 미스 시 MySQL 이력으로 폴백한다 | `CongestionServiceTest.redisMissDbHit`, `CongestionServiceTest.redisMissFallbackToDb` | PRD 3.2 | 자동 |
| CG-6 | Redis 와 MySQL 모두 비면 빈 결과를 반환한다 (예외 아님) | `CongestionServiceTest.redisMissDbMiss` | PRD 3.2 | 자동 |
| CG-7 | populationTime 이 null 인 레코드도 저장이 깨지지 않는다 | `CongestionServiceTest.saveAllWithNullPopulationTime` | 코드 | 자동 |
| CG-8 | 매일 03:00 크론이 7일 초과 이력을 삭제한다 | `DataCleanupSchedulerTest.cleanupOldData_success` | PRD 3.2 | 자동 |
| CG-9 | 정리 중 예외가 나도 다음 주기가 계속 돈다 | `DataCleanupSchedulerTest.cleanupOldData_exception` | 코드 | 자동 |
| CG-10 | 단건 조회는 없는 areaCode 에 404 를 준다 | `CongestionControllerTest.findByAreaCode_notFound` | 코드 | 자동 |
| CG-11 | 목록 응답에 ETag 를 붙이고 동일 ETag 재요청에 304 를 준다 | `CongestionControllerTest.findAll_returnsETag`, `CongestionControllerTest.findAll_notModified` | 결정 `../design-docs/decisions/010-controller-etag.md` | 자동 |
| CG-12 | 122개 장소 코드 목록에 중복이 없고 enum 이름과 코드가 일치한다 | `SeoulAreaTest.noDuplicates`, `SeoulAreaTest.codeMatchesEnumName` | PRD 3.2 | 자동 |
| CG-13 | 24시간 시간별 혼잡도 추이를 제공한다 | `CongestionAnalysisControllerTest.getHourlyTrend_success` | PRD 6.1 Should Have | 자동 |
| CG-14 | 요일별 평균 추이를 제공한다 | `CongestionAnalysisControllerTest.getDailyTrend_success` | PRD 6.1 Should Have | 자동 |
| CG-15 | 혼잡 순·한적 순 랭킹을 제공한다 | `CongestionAnalysisControllerTest.getBusiestRanking_success`, `CongestionAnalysisControllerTest.getRelaxedRanking_success` | PRD 6.1 Should Have | 자동 |
| CG-16 | 잘못된 areaCode 에 400 을 준다 | `CongestionAnalysisControllerTest.invalidAreaCode_returns400` | 코드 | 자동 |
| CG-17 | 시간별 평균 캐시가 미스 시 DB 로 가고, 평균 0 인 시간대는 제외한다 | `HourlyAvgCacheServiceTest.cacheMissDbHit`, `HourlyAvgCacheServiceTest.zeroAvgExcluded` | 코드 | 자동 |
| CG-18 | 캐시 파싱 실패·DB 예외가 조회 전체를 깨뜨리지 않는다 | `HourlyAvgCacheServiceTest.cacheParseError`, `HourlyAvgCacheServiceTest.dbException` | 코드 | 자동 |
| CG-19 | API Key 미설정 시 Stub 클라이언트가 더미 데이터를 제공한다 | 없음 | PRD 3.2 | 미충족 |

| ID | 확인할 것 | 방법 | 상태 |
|---|---|---|---|
| CG-M1 | 운영에서 5분 주기 수집이 실제로 돌고 Redis TTL 15분이 유효하다 | Grafana 대시보드에서 수집 주기와 캐시 히트율 확인 | 수동 |

CG-19 는 PRD 에 적힌 기능이고 코드에도 Stub 경로가 있지만 그것을 검증하는 테스트가 없다. 상태를 `미충족` 으로 둔 이유다.
