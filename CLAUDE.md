# dan-burn-go Backend

서울 주요 관광지·번화가 122곳의 실시간 혼잡도를 수집·분석하고, AI 혼잡 원인 분석과 대체 장소·교통 경로를 추천하는 마이크로서비스 백엔드. Java 21 / Spring Boot 3.4.5 5개 모듈 + Python 3.12 FastAPI 1개 모듈.

## 명령어
- 빌드: `./gradlew build`
- 검증(test + 문서 점검): `scripts/check.sh` ← 작업을 끝내기 전에 반드시 실행
- Java 테스트만: `./gradlew test`
- Python 테스트만: `cd service-ai && pytest`
- 단일 모듈 빌드: `./gradlew :service-congestion:build`
- 로컬 실행: `docker compose -f docker-compose.yml -f docker-compose.local.yml up -d`

lint 명령은 없다. spotless, checkstyle, ruff 설정이 저장소에 하나도 없어서 넣지 않았다. 도입하면 `scripts/check.sh` 와 `scripts/post-edit.sh` 에 같이 추가한다.

## 금지사항
- `main` 대상 PR 은 `dev` → `main` promotion 만. feature 브랜치에서 `main` 으로 직접 PR 하지 않는다
- `docker system prune --volumes` 금지. `backend_mysql-data`, `backend_rabbitmq-data` 가 삭제된다. 이미지만 정리할 때는 `docker image prune` 을 쓴다
- `mysql/init/**` 수정 금지. 컨테이너 최초 기동 시에만 실행되므로 기존 볼륨에는 반영되지 않는다. 스키마 변경은 애플리케이션 마이그레이션으로 처리한다
- 운영 배포는 CD 워크플로로만 한다. 서버에서 `docker compose up` 을 직접 실행하면 레포 상태와 어긋난다
- 위험 명령(`rm -rf`, `git push --force`, `git reset --hard` 등)은 직접 실행하지 말고 사용자에게 요청
- `.env`, `.infisical.json` 을 읽거나 커밋하지 않는다. 환경변수는 Infisical 이 주입한다

## 문서 지도
- 구조, 레이어, 의존 방향: `ARCHITECTURE.md`
- 무엇을, 왜 (PRD): `docs/product-specs/`
- 검증 가능한 수용 기준 (ERD, Engineering Requirements Document): `docs/requirements/`
- 어떻게 (설계): `docs/design-docs/`
- 왜 이렇게 정했나 (ADR): `docs/design-docs/decisions/`
- 무엇이 깨졌고 왜 (실패 기록): `docs/design-docs/failures/`
- 진행 중인 작업 계획: `docs/exec-plans/active/`
- 끝난 작업 기록: `docs/exec-plans/completed/`
- 기술부채: `docs/exec-plans/tech-debt.md`
- service-ai 모듈 상세(이벤트 흐름, 배치, MCP): `service-ai/CLAUDE.md`

## 작업 방식
- 여러 단계 작업은 `docs/exec-plans/active/` 에 계획을 먼저 쓰고, 끝나면 `completed/` 로 옮긴다
- 같은 실수가 두 번 나오면 이 문서에 규칙을 적고, 가능하면 `scripts/check.sh` 에 검사로 추가한다
- 결정(설계, 라이브러리 선택, 버린 대안)은 묻지 않고 ADR 로 남긴다. 실패는 실패 기록으로 남긴다
- 구현, 배포, 삭제로 상태가 바뀌면 같은 PR 에서 `docs/requirements/`(기준, 검증, 상태 칸)와 `docs/exec-plans/tech-debt.md` 를 고친다. 문서의 "현재 ~만 됨" 같은 문장도 같이 고친다
- PR 에 Gemini 리뷰를 받으려면 PR 댓글에 `/gemini review` 를 남긴다. push 만으로는 달리지 않는다

## Claude 전용 메모
- 이 저장소는 CodeGraph 로 인덱싱돼 있다(`.codegraph/`). 코드를 찾거나 이해할 때 grep·파일 읽기보다 `codegraph_explore` 를 먼저 쓴다
- 훅은 `scripts/guard.sh`(위험 명령 차단), `scripts/post-edit.sh`(수정 후 처리)를 호출한다. `.claude/settings.json` 변경은 세션을 다시 시작해야 적용된다
