# 001. 운영 서버 디스크 100% 로 전체 서비스 다운

- 날짜: 2026-09-12

## 증상
운영 서버의 모든 컨테이너가 내려갔다. `df -h /` 결과 19G 중 19G 사용, 여유 0.

## 원인
Docker 이미지·빌드 캐시 정리가 CD 워크플로의 `Pre-deploy disk cleanup` 스텝에만 붙어 있었다. 배포할 때만 정리가 돌기 때문에 2026-06-06 마지막 배포 이후 3개월간 정리가 한 번도 실행되지 않았다.

누적 구성(정리 후 측정): `/var` 9.5G(거의 Docker, 이 중 볼륨 4G 는 정리 대상 아님), `/usr` 2.4G, `/home` 2.3G(러너). `journald` 는 상한 미설정이어서 기본값(디스크 10%)까지 자랄 수 있었고, GitHub Actions 러너는 자동 업데이트 후 구버전 `bin.*`/`externals.*` 를 지우지 않아 674M 이 남아 있었다.

## 해결
1. 서버에서 수동 정리: `docker system prune -af --volumes` 6.1G + `journalctl --vacuum-size=200M` 164M
2. 러너가 online 으로 복귀하면서 queued 상태였던 `deploy` 잡이 이어서 실행되어 배포 완료 (CD run 34695868786, deploy 3분 18초, `service-congestion` 헬스체크 UP)
3. 러너 구버전 잔재 674M + `_work` 675M 추가 정리 → 100% 에서 71% 로

복구가 CD 재실행으로 안 된 이유는 `deploy` 잡이 운영 서버 자신(self-hosted runner)에서 돌기 때문이다. 서버가 죽으면 러너도 offline 이라 잡이 시작되지 않는다. 서버에 직접 접속해 디스크를 비우는 것이 먼저다.

이번 수동 정리에서 `docker system prune -af --volumes` 를 썼는데, 이 명령은 `backend_mysql-data` 를 지울 수 있었다. 삭제된 볼륨이 해시 이름의 익명 볼륨 하나뿐이어서 데이터는 살았지만, 운이 좋았던 쪽이다. 이후 CLAUDE.md 금지사항에 `--volumes` 를 넣었다.

## 재발 방지
- 운영 서버 root crontab 2줄 추가. 배포와 분리된 스케줄이라 배포가 없어도 돈다
  - 매일 04시: 사용량 80% 이상일 때만 7일 지난 이미지·빌드 캐시 prune (`--volumes` 없음)
  - 매월 1일 05시: 러너 자동 업데이트가 남긴 구버전 `bin.*`/`externals.*` 정리 (심링크가 가리키는 현재 버전은 제외)
- `journald` 에 `SystemMaxUse=500M` 설정
- CLAUDE.md 금지사항에 `docker system prune --volumes` 추가
- CD 에 `workflow_dispatch` 추가 (`../decisions/009-cd-self-hosted-runner.md`)

crontab 2줄은 이 저장소가 아니라 서버에만 있다. 재구축 시 사라지는 문제는 기술부채로 남겼다.

## 관련
- `../decisions/009-cd-self-hosted-runner.md`, `docs/exec-plans/tech-debt.md`
