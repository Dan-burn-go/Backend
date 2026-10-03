# 009. CD 에 workflow_dispatch 를 추가해 수동 재배포 경로를 만든다

- 날짜: 2026-09-12
- 상태: 채택

## 배경
CD 는 `main` push 로만 실행됐다. 운영 서버가 디스크 풀로 내려갔을 때 재배포를 걸 방법이 없었다. 마지막 실행은 3개월 전이어서 GitHub 의 re-run 도 막혀 있었다(생성 후 한 달 지난 실행은 재실행 불가).

## 결정
`.github/workflows/cd.yml` 에 `workflow_dispatch` 를 추가해 Actions 탭에서 수동 실행할 수 있게 한다.

## 근거
- push 트리거만 두면 재배포를 위해 의미 없는 커밋을 만들어야 한다
- re-run 에 기간 제한이 있어 과거 실행 재사용은 복구 수단이 될 수 없다

## 결과/영향
- 이것으로도 서버가 죽은 상황은 복구되지 않는다. `deploy` 잡이 운영 서버 자신(self-hosted runner)에서 돌기 때문에 서버가 offline 이면 잡이 queued 로 멈춘다. 디스크를 비워 러너를 올리는 것이 먼저다
- 러너가 살아나면 queued 된 잡이 20분 타임아웃 안이면 그대로 이어서 배포된다

## 관련
- PR #373(workflow_dispatch 추가), #374(dev→main promotion)
- `docs/design-docs/failures/001-disk-full-outage.md`
