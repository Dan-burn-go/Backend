# 기술부채

| 항목 | 위치 | 영향 | 발견일 |
|---|---|---|---|
| 배치 윈도우 값이 문서 3곳에서 다 다름. README 10초, `service-ai/CLAUDE.md` 2초·max_size 10, 실제 코드 5초·max_size 3 | `service-ai/app/config.py:52-53`, `README.md`, `service-ai/CLAUDE.md` | 문서를 믿고 튜닝하면 틀린 값을 기준으로 판단한다 | 2026-10-04 |
| lint·포맷 도구가 없다. spotless·checkstyle·ruff 설정이 하나도 없어 `scripts/check.sh` 의 lint 단계와 `post-edit.sh` 의 포맷 case 가 비어 있다 | `build.gradle`, `service-ai/` | 코드 스타일이 리뷰어 눈에만 의존한다 | 2026-10-04 |
| CD 의 `deploy` 잡이 운영 서버 자신(self-hosted runner)에서 돈다. 서버가 죽으면 CD 로 복구할 수 없다 | `.github/workflows/cd.yml` | 장애 복구가 수동 SSH 에 의존 | 2026-09-12 |
| 디스크 정리가 저장소 밖 서버 root crontab 에만 있다. 서버를 재구축하면 사라진다 | 운영 서버 `sudo crontab -l` | 정리가 조용히 멈춰도 알 방법이 없다 | 2026-09-12 |
| 운영 디스크 19G 에 정상 상태가 약 71%. 배포 1회가 이미지 재다운로드로 2~3G 를 쓴다 | 운영 서버 | 여유가 적어 한 번의 이상 증가로 100% 에 닿는다 | 2026-09-12 |
| `docs/requirements/` 가 비어 있다. PRD 기능이 검증 가능한 수용 기준으로 전개되지 않았다 | `docs/requirements/` | 무엇이 어떤 테스트로 보장되는지 추적 불가 | 2026-10-04 |
| CI 가 service-ai 의 Python 테스트를 돌리지 않는다. 테스트 파일 20개가 있는데 `ci.yml` 은 gradle build 와 docker build 만 한다 | `.github/workflows/ci.yml` | Python 쪽 회귀가 CI 에서 안 잡힌다 | 2026-10-04 |
| `pytest` 가 전역에 없고 `service-ai/.venv` 에만 있다. `scripts/check.sh` 는 그 경로를 직접 쓴다 | `service-ai/.venv` | venv 를 안 만든 사람은 검증을 돌릴 수 없다 | 2026-10-04 |
| PRD v1.4 가 낡았다. Map·Mobility·AI 가 "개발 예정" 으로, 6장 백로그 기능이 전부 `Backlog` 상태로 적혀 있으나 실제로는 대체지 추천·경로 추천·AI 분석·트렌드·랭킹·문화정보·알림까지 구현돼 테스트가 있다 | `docs/product-specs/PRD.md` | PRD 를 보고 범위를 판단하면 이미 있는 기능을 다시 만들거나 없는 기능을 있다고 본다 | 2026-10-04 |
| 배치 윈도우 문서가 네 곳에서 갈린다. PRD 6.4 와 `service-ai/CLAUDE.md` 는 2초, README 는 10초, 코드는 5초 | `service-ai/app/config.py:52`, `docs/product-specs/PRD.md`, `README.md`, `service-ai/CLAUDE.md` | 위 기존 항목의 정확한 범위 | 2026-10-04 |
| 수용 기준 3건이 테스트 없이 `미충족` 이다. Stub 클라이언트 폴백(CG-19), 대체지 소요시간 정렬(MP-18), 경로 소요시간 정렬·추천 표시(MB-11) | `docs/requirements/` | PRD 수용 조건인데 무엇이 보장하는지 없다 | 2026-10-04 |
