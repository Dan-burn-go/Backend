# ── Build Stage ────────────────────────────────────────────────────────────────
FROM gradle:8.12-jdk21-alpine AS builder

WORKDIR /app

# Copy root build scripts
COPY settings.gradle build.gradle ./

# Copy shared library first (layer-cache friendly)
COPY service-common/build.gradle service-common/build.gradle
COPY service-common/src           service-common/src

# Copy core module
COPY service-core/build.gradle service-core/build.gradle
COPY service-core/src          service-core/src

RUN gradle :service-core:bootJar --no-daemon -x test

# ── Runtime Stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# CVE-2026-22184 zlib Buffer Overflow 취약점 해결
RUN apk update && apk upgrade && \
    apk add --no-cache zlib>=1.3.2-r0

WORKDIR /app

COPY --from=builder /app/service-core/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
