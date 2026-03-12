# ── Build Stage ────────────────────────────────────────────────────────────────
FROM gradle:8.12-jdk21-alpine AS builder

WORKDIR /app

# Copy root build scripts
COPY settings.gradle build.gradle ./

# Copy shared library first (layer-cache friendly)
COPY service-common/build.gradle service-common/build.gradle
COPY service-common/src           service-common/src

# Copy gateway module
COPY service-gateway/build.gradle service-gateway/build.gradle
COPY service-gateway/src          service-gateway/src

RUN gradle :service-gateway:bootJar --no-daemon -x test

# ── Runtime Stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/service-gateway/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
