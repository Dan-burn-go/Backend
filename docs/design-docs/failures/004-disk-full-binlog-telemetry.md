# 004. 디스크가 다시 가득 차서 MySQL 종료, 백엔드 unhealthy

- 날짜: 2026-11-17

## 증상
18:04(KST)에 디스크가 가득 차면서 MySQL 이 종료 코드 2 로 꺼졌다. 재시작 정책이 없어서 그대로 꺼진 상태로 남았고, MySQL 에 의존하는 백엔드 컨테이너가 unhealthy 가 됐다. 진단용 컨테이너도 `no space left on device` 로 뜨지 않았다.

## 원인
001 에서 추가한 crontab 은 이미지와 빌드 캐시만 정리한다. 이번에 디스크를 채운 건 그 대상이 아닌 두 곳이었다.
- MySQL binlog: MySQL 8.0 기본 보관 기간이 30일이다. binlog.000027~029 만 약 3.3GB 였다.
- observability(`grafana/otel-lgtm`): 컨테이너 안 `/data` 에 약 2.6GB 가 쌓였다. Loki 는 보존 기간 기본값이 무제한이고, Prometheus 는 15일 기준으로만 지우며 용량 상한이 없다.

compose 의 어떤 서비스에도 `restart` 가 없어서, 공간이 생겨도 꺼진 컨테이너가 다시 올라오지 않았다.

## 해결
1. MySQL 이 꺼진 상태에서 binlog.000027~029 를 지우고 `binlog.index` 에서도 같은 줄을 지웠다. 꺼져 있어서 `PURGE` 를 쓸 수 없었다
2. MySQL 을 시작하고 `SET PERSIST binlog_expire_logs_seconds=259200` 를 실행했다

## 재발 방지 (docker-compose.yml)
- mysql: `--binlog-expire-logs-seconds=259200` (3일). 복제나 CDC 를 쓰지 않지만, binlog 를 끄지 않고 짧게 남겨서 시점 복구 여지를 둔다
- observability: Prometheus 는 `7d` 또는 `3GB` 중 먼저 닿는 쪽, Loki 는 compactor retention 을 켜서 `168h` 동안 보관한다. 이미지가 제공하는 `PROMETHEUS_EXTRA_ARGS` / `LOKI_EXTRA_ARGS` 를 써서 설정 파일을 덮어쓰지 않았다. Tempo 는 기본값 14일로 이미 상한이 있어서 손대지 않았다
- 모든 서비스에 `restart: unless-stopped` 를 넣었다

`/data` 를 볼륨으로 빼는 건 하지 않았다. 보존 기간이 있으면 용량에 상한이 생기고, 텔레메트리는 재생성 시 사라져도 되기 때문이다.

## 관련
- `001-disk-full-outage.md`
