# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

COPY services/circleguard-notification-service services/circleguard-notification-service

RUN sed -i 's/\r//' gradlew && chmod +x gradlew && \
    ./gradlew :services:circleguard-notification-service:bootJar -x test --no-daemon

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/services/circleguard-notification-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
