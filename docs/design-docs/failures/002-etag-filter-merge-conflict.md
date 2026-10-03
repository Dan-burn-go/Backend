# 002. 병합 충돌 해결에서 제거했던 ETag 필터 Bean 이 되살아날 위험

- 날짜: 2026-06-06

## 증상
`CacheConfig` 에서 병합 충돌이 났다. 한쪽은 `ShallowEtagHeaderFilter` Bean 을 제거한 상태(의도된 변경), 다른 쪽은 그 Bean 이 남은 상태였다.

## 원인
ETag 처리를 필터에서 컨트롤러로 옮기는 변경(`../decisions/010-controller-etag.md`)과 다른 브랜치의 `CacheConfig` 수정이 같은 파일에서 만났다. 충돌을 기계적으로 해결하면 제거한 Bean 이 되살아나 필터와 컨트롤러가 ETag 를 이중으로 처리한다.

## 해결
충돌을 `ours` 로 해결해 Bean 제거 상태를 유지했다 (커밋 `9f7078d`).

## 재발 방지
- 304 동작을 고정하는 회귀 테스트를 넣었다 (`CongestionControllerTest.findAll_returnsETag`, `findAll_notModified`, 커밋 `b60e821`). 필터가 되살아나 동작이 바뀌면 테스트가 깨진다
- 요구사항에 기준으로 올렸다 (`../../requirements/congestion.md` CG-11)

"설정 Bean 을 의도적으로 제거한 변경" 은 병합 충돌에서 되돌려지기 쉽다. 제거 의도를 테스트로 고정하지 않으면 다음 충돌에서 다시 잃는다.

## 관련
- `../decisions/010-controller-etag.md`
