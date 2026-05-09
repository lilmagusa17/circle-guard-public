# ---- Build Stage ----
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

COPY services/circleguard-form-service services/circleguard-form-service

RUN sed -i 's/\r//' gradlew && chmod +x gradlew && \
    ./gradlew :services:circleguard-form-service:bootJar -x test --no-daemon

# ---- Runtime Stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /workspace/services/circleguard-form-service/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
