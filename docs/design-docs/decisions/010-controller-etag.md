# 010. 목록 응답의 ETag/304 를 서블릿 필터 대신 컨트롤러에서 직접 처리한다

- 날짜: 2026-06-06
- 상태: 채택

## 배경
혼잡도 목록은 5분마다 한 번 바뀌는데 클라이언트가 그보다 자주 조회한다. 조건부 요청으로 트래픽을 줄이려고 Spring 의 `ShallowEtagHeaderFilter` 를 썼지만 의도대로 동작하지 않았다.

## 결정
`ShallowEtagHeaderFilter` Bean 을 제거하고 컨트롤러에서 ETag 를 직접 계산해 304 를 반환한다. 필터를 우회하는 방향이다.

## 근거
- 필터 방식은 응답 본문을 전부 만들어 버퍼에 담은 뒤 해시를 뜬다. 직렬화 비용이 그대로 남아 절약 효과가 작다
- 컨트롤러에서 처리하면 304 경로에서 본문 생성을 건너뛸 수 있다
- 회귀를 막는 테스트를 같이 넣었다 (`CongestionControllerTest.findAll_returnsETag`, `findAll_notModified`)

버린 대안: `ShallowEtagHeaderFilter`. 위 이유로 뺐다.

## 결과/영향
- ETag 계산 책임이 컨트롤러에 생겼다. 다른 목록 API 에 같은 처리를 하려면 각각 구현해야 한다
- `CacheConfig` 병합 충돌 시 필터 Bean 을 되살리지 않도록 주의해야 한다 (`../failures/002-etag-filter-merge-conflict.md`)

## 관련
- 커밋 `cac5915`, `b60e821` (#350 회귀 테스트), `9f7078d` (병합 충돌 해결)
- 요구사항 `../../requirements/congestion.md` CG-11
