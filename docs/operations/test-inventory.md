# CircleGuard – Test Inventory

This document inventories all automated tests across CircleGuard's 8 microservices.
Last updated: Phase 6 (Testing Enhancement).

---

## Summary

| Type        | Count | Framework              |
|-------------|-------|------------------------|
| Unit        | 30+   | JUnit 5, Mockito       |
| Integration | 5     | JUnit 5, Testcontainers |
| E2E         | 5     | JUnit 5 (Spring Boot Test) |
| Performance | 1 file (3 user classes) | Locust |

---

## Unit Tests (per service)

### auth-service
- `JwtTokenServiceTest` — token generation, validation, expiry
- `LoginControllerTest` — login endpoint success/failure paths

### dashboard-service
- `AnalyticsServiceTest` — geospatial analytics logic
- `KAnonymityFilterTest` — privacy-preserving k-anonymity algorithm
- `AnalyticsControllerTest` — REST controller layer

### file-service
- `FileStorageServiceTest` — file storage and retrieval logic
- `FileUploadControllerTest` — upload endpoint validation

### form-service
- `HealthSurveyServiceTest` — survey submission logic
- `SymptomMapperTest` — symptom DTO mapping
- `HealthSurveyControllerTest` — survey REST controller
- `QuestionnaireControllerTest` — questionnaire REST controller
- `AttachmentControllerTest` — attachment handling

### gateway-service
- Basic unit tests for QR validation and Redis session logic

### identity-service
- Basic unit tests for identity/vault operations

### notification-service
- `TemplateServiceTest` — notification template rendering
- `NotificationDispatcherTest` — dispatch routing logic
- `PriorityAlertListenerTest` — Kafka alert.priority consumer
- `ExposureNotificationListenerTest` — exposure event consumer
- `NotificationRetryTest` — retry mechanism
- `LmsServiceTest` — LMS integration
- `RoomReservationServiceTest` — room reservation notifications

### promotion-service
- `HealthStatusServiceTest` — health status lifecycle
- `FloorServiceTest` — floor-level proximity logic
- `HealthStatusReevaluationTest` — status re-evaluation on new survey
- `StatusLifecycleTest` — full lifecycle state machine
- `AdministrativeCorrectionTest` — admin override tests
- `SurveyListenerTest` — Kafka survey.submitted consumer
- `HealthStatusControllerTest` — REST controller

---

## Integration Tests

These tests use Testcontainers (Postgres, Redis, Kafka, Neo4j) to verify component interactions.

| Test Class                     | Service             | Containers Used           |
|--------------------------------|---------------------|---------------------------|
| `AuthLoginIntegrationTest`     | auth-service        | Postgres                  |
| `DashboardIntegrationTest`     | dashboard-service   | Postgres                  |
| `FileUploadIntegrationTest`    | file-service        | Postgres                  |
| `FormKafkaIntegrationTest`     | form-service        | Kafka, Postgres            |
| `NotificationKafkaIntegrationTest` | notification-service | Kafka                |

Run with: `./gradlew test -Pintegration --no-daemon`

---

## End-to-End (E2E) Tests

These tests exercise full request/response flows across service boundaries.

| Test Class                  | Service             | Scenario |
|-----------------------------|---------------------|----------|
| `AuthLoginE2ETest`          | auth-service        | Full login → JWT → protected endpoint |
| `DashboardAnalyticsE2ETest` | dashboard-service   | Form submission → analytics aggregation |
| `FileUploadDownloadE2ETest` | file-service        | Upload → store → retrieve file |
| `HealthSurveyE2ETest`       | form-service        | Submit survey → Kafka event → state change |
| `PromotionStatusE2ETest`    | promotion-service   | Location signal → status promotion → Neo4j |

Run with: `./gradlew test -Pe2e --no-daemon`

---

## Performance Tests (Locust)

**File:** `tests/performance/locustfile.py`

Three user classes targeting different services:

| Class                   | Service            | Endpoints                                    |
|-------------------------|--------------------|----------------------------------------------|
| `FormServiceUser`       | form-service       | `POST /api/v1/surveys`, `GET /api/v1/questionnaires/active` |
| `GatewayServiceUser`    | gateway-service    | `POST /api/v1/gate/validate`                 |
| `PromotionServiceUser`  | promotion-service  | `POST /api/v1/location/signal`, `GET /api/v1/health-status/stats` |

**Results from Taller 2 (pre-GKE baseline):**

| Metric    | Value       |
|-----------|-------------|
| Requests  | 2,558       |
| RPS       | 21.77       |
| Median    | 230 ms      |
| Fail rate | 0%          |

**GKE execution:**
```bash
TARGET_SERVICE=form \
FORM_SERVICE_URL=http://34.58.31.128 \
locust -f tests/performance/locustfile.py --headless -u 50 -r 5 -t 60s
```

---

## Test Configuration Gradle Flags

| Flag         | Behavior                               |
|--------------|----------------------------------------|
| (none)       | Unit tests only                        |
| `-Pintegration` | Integration test subset            |
| `-Pe2e`      | E2E test subset                        |

---

## Coverage Policy

See [`docs/operations/coverage-policy.md`](coverage-policy.md) for thresholds and Jenkins publishing configuration.
