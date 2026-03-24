# ── Build Stage ────────────────────────────────────────────────────────────────
FROM gradle:8.12-jdk21-alpine AS builder

WORKDIR /app

# 루트 빌드 스크립트 복사
COPY settings.gradle build.gradle ./
COPY service-common/build.gradle service-common/build.gradle
COPY service-gateway/build.gradle service-gateway/build.gradle

# 의존성만 먼저 다운로드하여 레이어 캐싱 (build.gradle 변경 시에만 재실행)
RUN gradle :service-gateway:dependencies --no-daemon -q

COPY service-common/src           service-common/src
COPY service-gateway/src          service-gateway/src

RUN gradle :service-gateway:bootJar --no-daemon -x test

# ── Runtime Stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# CVE-2026-22184 zlib Buffer Overflow 취약점 해결
RUN apk update && apk upgrade && \
    apk add --no-cache zlib>=1.3.2-r0

WORKDIR /app

COPY --from=builder /app/service-gateway/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
