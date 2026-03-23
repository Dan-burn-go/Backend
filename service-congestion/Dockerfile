# ── Build Stage ────────────────────────────────────────────────────────────────
FROM gradle:8.12-jdk21-alpine AS builder

WORKDIR /app

COPY settings.gradle build.gradle ./
COPY service-common/build.gradle service-common/build.gradle
COPY service-common/src           service-common/src
COPY service-congestion/build.gradle service-congestion/build.gradle
COPY service-congestion/src          service-congestion/src

RUN gradle :service-congestion:bootJar --no-daemon -x test

# ── Runtime Stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN apk update && apk upgrade && \
    apk add --no-cache zlib>=1.3.2-r0

WORKDIR /app
COPY --from=builder /app/service-congestion/build/libs/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
