# ── Build Stage ────────────────────────────────────────────────────────────────
FROM gradle:8.12-jdk21-alpine AS builder

WORKDIR /app

COPY settings.gradle build.gradle ./
COPY service-common/build.gradle service-common/build.gradle
COPY service-transport/build.gradle service-transport/build.gradle

# 의존성만 먼저 다운로드하여 레이어 캐싱 (build.gradle 변경 시에만 재실행)
RUN gradle :service-transport:dependencies --no-daemon -q

COPY service-common/src           service-common/src
COPY service-transport/src          service-transport/src

RUN gradle :service-transport:bootJar --no-daemon -x test

# ── Runtime Stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN apk update && apk upgrade && \
    apk add --no-cache zlib>=1.3.2-r0

WORKDIR /app
COPY --from=builder /app/service-transport/build/libs/*.jar app.jar

EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
