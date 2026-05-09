# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy gradle wrapper and config first (layer cache)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy only the target service source
COPY services/circleguard-file-service services/circleguard-file-service

RUN sed -i 's/\r//' gradlew && chmod +x gradlew && \
    ./gradlew :services:circleguard-file-service:bootJar -x test --no-daemon

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/services/circleguard-file-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
