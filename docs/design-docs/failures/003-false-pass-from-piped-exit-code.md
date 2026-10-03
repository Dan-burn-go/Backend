# 003. 파이프로 가린 종료 코드 때문에 실패한 검증을 통과로 보고

- 날짜: 2026-10-04

## 증상
하네스 세팅 직후 `scripts/check.sh` 를 `scripts/check.sh 2>&1 | tail -40` 로 실행하고 종료 코드 0 을 근거로 "검증 통과" 라고 보고했다. 실제로는 `pytest: command not found` 로 중단돼 Python 테스트와 `check-docs.sh` 가 아예 실행되지 않았다.

## 원인
두 가지가 겹쳤다.

1. 파이프라인의 종료 코드는 마지막 명령(`tail`)의 것이다. `check.sh` 가 실패해도 `tail` 이 성공하면 0 이 된다. 호출 쪽에 `pipefail` 이 없었다
2. `pytest` 가 전역에 없고 `service-ai/.venv` 에만 있었다. `check.sh` 는 `pytest` 를 그냥 호출했다

출력 끝에 `pytest: command not found` 가 찍혀 있었는데 종료 코드만 보고 넘어갔다.

## 해결
- `check.sh` 가 `service-ai/.venv/bin/pytest` 를 쓰도록 고쳤다. venv 가 없으면 조용히 건너뛰지 않고 설치 명령을 띄우며 `exit 1`
- 재실행해 실제 결과를 확인했다. Gradle BUILD SUCCESSFUL, pytest 145 passed, `docs/requirements 점검 통과`

## 재발 방지
- 검증 명령은 파이프를 걸지 않고 종료 코드를 직접 확인한다. 출력을 줄여야 하면 파일로 보내고 종료 코드를 따로 읽는다
- 도구 부재를 "건너뜀" 으로 처리하지 않는다. `check.sh` 의 pytest 분기는 실패로 끝난다

종료 코드는 그것을 만든 명령이 무엇인지 확인하고 읽어야 한다. 0 이 "통과" 라는 뜻이 되는 건 그 0 이 검증 명령의 것일 때만이다.

## 관련
- `docs/exec-plans/tech-debt.md` (pytest 가 venv 에만 있는 항목)
