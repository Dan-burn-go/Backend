# 교통 경로 추천 (service-mobility)

출처: `docs/product-specs/PRD.md` 3.4, 6.3

| ID | 성립해야 할 것 | 검증 | 출처 | 상태 |
|---|---|---|---|---|
| MB-1 | 출발지·목적지 좌표로 경로 목록을 반환한다 | `OdsayControllerTest.valid_params_returns_200`, `OdsayControllerTest.response_paths_reflected_in_data` | PRD 6.3 Must Have | 자동 |
| MB-2 | 좌표 파라미터가 빠지면 400 을 준다 | `OdsayControllerTest.missing_originLng_returns_400`, `OdsayControllerTest.missing_destLat_returns_400` | 코드 | 자동 |
| MB-3 | 좌표가 허용 범위를 벗어나면 400 을 준다 | `OdsayControllerTest.originLng_out_of_range_returns_400`, `OdsayControllerTest.originLat_out_of_range_returns_400` | 코드 | 자동 |
| MB-4 | 버스·지하철·도보 구간을 각각 응답 모델로 매핑한다 | `OdsayResponseMapperTest.bus_subPath_maps_all_fields`, `OdsayResponseMapperTest.subway_subPath_maps_all_fields`, `OdsayResponseMapperTest.walk_subPath_optional_fields_are_null` | 코드 | 자동 |
| MB-5 | 노선·정류장 정보가 없는 구간은 null 로 두고 매핑을 깨뜨리지 않는다 | `OdsayResponseMapperTest.non_walk_with_null_lane_returns_null_lanes`, `OdsayResponseMapperTest.non_walk_with_null_passStopList_returns_null_stations` | 코드 | 자동 |
| MB-6 | 경로가 여러 개면 모두 매핑한다 | `OdsayResponseMapperTest.multiple_paths_all_mapped` | 코드 | 자동 |
| MB-7 | 외부 API 응답이 null 이거나 네트워크 오류면 서버 예외로 변환한다 | `OdsayApiClientTest.null_response_throws_odsay_server_exception`, `OdsayApiClientTest.network_error_throws_odsay_server_exception` | 코드 | 자동 |
| MB-8 | 경로 결과가 없으면 404 를 준다 | `OdsayApiClientTest.null_result_throws_global_exception_404` | 결정 `../design-docs/decisions/003-circuit-breaker-msa-call.md` | 자동 |
| MB-9 | 서킷 폴백은 503 이 아니라 500 으로 응답한다 | `OdsayServiceTest.fallback_on_server_exception_throws_500`, `OdsayServiceTest.fallback_on_runtime_exception_throws_500` | 결정 003 | 자동 |
| MB-10 | 폴백에서 정의된 예외는 상태코드를 보존해 그대로 올린다 | `OdsayServiceTest.fallback_on_global_exception_rethrows` | 결정 003 | 자동 |
| MB-11 | 소요시간 순 정렬로 가장 빠른 경로에 추천 표시를 한다 | 없음 | PRD 6.3 Must Have 2번 | 미충족 |

MB-11 은 PRD 수용 조건이지만 정렬·추천 표시를 검증하는 테스트가 없다.
