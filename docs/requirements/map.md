# 장소 탐색·대체지 추천 (service-map)

출처: `docs/product-specs/PRD.md` 3.3, 6.2

PRD 3.3 은 이 서비스를 "개발 예정" 으로 적어 두었으나 대체지 추천·주변 장소·문화 정보가 모두 구현돼 테스트가 있다.

| ID | 성립해야 할 것 | 검증 | 출처 | 상태 |
|---|---|---|---|---|
| MP-1 | 혼잡 구역 선택 시 인근 대체지 목록에 혼잡도를 함께 준다 | `AlternativeLocationServiceTest.returnsListWithCongestionLevel` | PRD 6.2 Must Have | 자동 |
| MP-2 | 대체지를 혼잡도가 낮은 순으로 정렬한다 | `AlternativeLocationServiceTest.sortsByCongestionLevelOrder` | PRD 6.2 Must Have | 자동 |
| MP-3 | 혼잡도를 못 받은 후보는 목록 뒤로 보내되 제외하지 않는다 | `AlternativeLocationServiceTest.nullCongestion_sortedLast`, `AlternativeLocationServiceTest.congestionApiEmpty_returnsNullCongestionLevel` | 결정 `../design-docs/decisions/003-circuit-breaker-msa-call.md` | 자동 |
| MP-4 | 반경 내 후보가 없으면 빈 목록을 준다 (예외 아님) | `AlternativeLocationServiceTest.noAlternatives_returnsEmptyList` | 코드 | 자동 |
| MP-5 | 없는 areaCode 는 404 를 준다 | `AlternativeLocationServiceTest.unknownAreaCode_throwsGlobalException404`, `AlternativeLocationControllerTest.getAlternativeLocation_invalidAreaCode_notFound` | 코드 | 자동 |
| MP-6 | 혼잡도 서비스 장애 시 503 이 아니라 정의된 응답으로 떨어진다 | `AlternativeLocationControllerTest.getAlternativeLocation_serviceUnavailable` | 결정 003 | 자동 |
| MP-7 | 필수 파라미터가 없으면 400 을 준다 | `AlternativeLocationControllerTest.getAlternativeLocation_missingParam_badRequest` | 코드 | 자동 |
| MP-8 | 주변 장소를 카테고리별로 조회하고, 전체 카테고리는 4개 API 를 병합한다 | `NearbyPlaceServiceTest.allCategory_callsFourApisAndMergesResults` | PRD 6.2 Could Have | 자동 |
| MP-9 | 주변 장소 결과를 Redis 에 TTL 과 함께 캐싱하고 히트 시 외부 API 를 호출하지 않는다 | `NearbyPlaceServiceTest.cacheMiss_savesToRedisWithTtl`, `NearbyPlaceServiceTest.cacheHit_returnsRedisData_withoutCallingKakao` | 코드 | 자동 |
| MP-10 | Redis 읽기 실패는 외부 API 폴백으로, 쓰기 실패는 정상 응답으로 흡수한다 | `NearbyPlaceServiceTest.redisReadFailure_fallbackToKakao`, `NearbyPlaceServiceTest.redisWriteFailure_returnsResultNormally` | 코드 | 자동 |
| MP-11 | areaCode → 내부 ID·좌표 매핑이 없는 코드에 빈 값을 준다 | `LocationCodeMapperTest.unknownAreaCode_returnsEmpty`, `LocationCodeMapperTest.areaCodeWithNullCoordinate_returnsEmpty` | 코드 | 자동 |
| MP-12 | 반경 내 진행 중인 문화 행사를 제공한다 | `CultureEventServiceTest.eventsWithinRadius_returnsList`, `CultureEventServiceTest.callsRepositoryWithCorrectRadius` | PRD 6.2 Should Have | 자동 |
| MP-13 | 좌표 범위를 벗어난 요청은 400 을 준다 | `CultureEventControllerTest.getCultureEvents_latitudeOverMax_badRequest`, `CultureEventControllerTest.getCultureEvents_longitudeUnderMin_badRequest` | 코드 | 자동 |
| MP-14 | 행사 동기화는 기존 행사를 갱신하고 새 행사는 추가한다 | `EventUpsertServiceTest.existingEvent_updatedAndSaved`, `EventUpsertServiceTest.newEvent_savedToRepository` | 코드 | 자동 |
| MP-15 | 종료된 행사와 날짜가 없는 행사는 건너뛴다 | `EventUpsertServiceTest.expiredEvent_skipped`, `EventUpsertServiceTest.nullStartDate_skipped`, `EventUpsertServiceTest.nullEndDate_skipped` | 코드 | 자동 |
| MP-16 | 갱신 시 좌표가 없으면 기존 좌표를 유지한다 | `EventUpsertServiceTest.existingEvent_nullCoordinate_keepsExistingCoordinate` | 코드 | 자동 |
| MP-17 | 행사 API 예외가 나도 동기화 배치가 중단되지 않고 정리 단계까지 간다 | `EventServiceTest.apiException_logsAndContinues_deletesCalled` | 코드 | 자동 |
| MP-18 | 소요시간 순 정렬 (외부 길찾기 API 조회 결과 기준) | 없음 | PRD 6.2 Must Have 2번 | 미충족 |

| ID | 확인할 것 | 방법 | 상태 |
|---|---|---|---|
| MP-M1 | MySQL `ST_Distance_Sphere` 반경 2km 추출이 실제 좌표에서 맞다 | 운영 DB 에서 알려진 좌표로 쿼리해 결과 확인 | 수동 |

MP-18 은 PRD 수용 조건이지만 현재 정렬 기준은 혼잡도순(MP-2)이다. 소요시간 정렬을 검증하는 테스트가 없다.
