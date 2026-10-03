#!/usr/bin/env bash
# test + 문서 점검을 한 번에 실행한다. 에이전트와 CI가 같은 명령을 쓴다.
# lint 단계는 없다. spotless/checkstyle/ruff 설정이 저장소에 없기 때문이다.
set -euo pipefail
cd "$(dirname "$0")/.."

# Java 5개 모듈 테스트
./gradlew test

# Python AI 서비스 테스트 (pytest 는 service-ai/.venv 에 있다)
if [ -x service-ai/.venv/bin/pytest ]; then
  (cd service-ai && .venv/bin/pytest)
else
  echo "service-ai/.venv/bin/pytest 없음. python3 -m venv service-ai/.venv && service-ai/.venv/bin/pip install -r service-ai/requirements-dev.txt" >&2
  exit 1
fi

# 문서 (ERD 상태 칸, 테스트 참조)
scripts/check-docs.sh
