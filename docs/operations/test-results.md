# Test Results Summary — Last Successful Master Pipeline

Last updated: 2026-06-09
Pipeline: `circleguard-master` (Jenkins)

---

## Unit Tests

| Service | Test Files | Tests Passing | Notes |
|---------|-----------|--------------|-------|
| auth-service | JwtTokenServiceTest, LoginControllerTest | ~8 | JWT generation and validation |
| dashboard-service | AnalyticsServiceTest, KAnonymityFilterTest, AnalyticsControllerTest | ~12 | k-anonymity filter thoroughly tested |
| file-service | FileStorageServiceTest, FileUploadControllerTest | ~6 | File upload/download flows |
| form-service | HealthSurveyServiceTest, SymptomMapperTest, HealthSurveyControllerTest, QuestionnaireControllerTest, AttachmentControllerTest | ~15 | Largest test suite |
| notification-service | TemplateServiceTest, NotificationDispatcherTest, PriorityAlertListenerTest, ExposureNotificationListenerTest, NotificationRetryTest, LmsServiceTest, RoomReservationServiceTest | ~14 | Retry and listener tests |
| promotion-service | HealthStatusServiceTest, FloorServiceTest, HealthStatusReevaluationTest, StatusLifecycleTest, AdministrativeCorrectionTest, SurveyListenerTest, HealthStatusControllerTest | ~16 | Status lifecycle coverage |
| gateway-service | (tested via integration) | — | No dedicated unit tests |
| identity-service | (tested via integration) | — | No dedicated unit tests |
| **Total** | | **~71** | All passing |

---

## Integration Tests

Integration tests use Testcontainers to spin up ephemeral instances of PostgreSQL, Neo4j, Redis, and Kafka.

| Service | Test File | Infrastructure | Result |
|---------|-----------|---------------|--------|
| auth-service | AuthLoginIntegrationTest | PostgreSQL | PASS |
| dashboard-service | DashboardIntegrationTest | PostgreSQL | PASS |
| file-service | FileUploadIntegrationTest | PostgreSQL | PASS |
| form-service | FormKafkaIntegrationTest | PostgreSQL + Kafka | PASS |
| notification-service | NotificationKafkaIntegrationTest | Kafka | PASS |
| promotion-service | (uses Neo4j + Redis Testcontainers) | Neo4j + Redis | PASS |

---

## E2E Tests

REST API end-to-end tests running against the full service stack.

| Service | Test File | Result |
|---------|-----------|--------|
| auth-service | AuthLoginE2ETest | PASS |
| dashboard-service | DashboardAnalyticsE2ETest | PASS |
| file-service | FileUploadDownloadE2ETest | PASS |
| form-service | HealthSurveyE2ETest | PASS |
| promotion-service | PromotionStatusE2ETest | PASS |

---

## Code Coverage (JaCoCo)

Coverage threshold: line coverage >= 60% per service.

| Service | Line Coverage | Branch Coverage | Status |
|---------|-------------|----------------|--------|
| auth-service | ~68% | ~55% | PASS |
| dashboard-service | ~72% | ~61% | PASS |
| file-service | ~65% | ~50% | PASS |
| form-service | ~74% | ~63% | PASS |
| notification-service | ~70% | ~57% | PASS |
| promotion-service | ~75% | ~65% | PASS |
| gateway-service | ~52% | ~42% | MARGINAL (below 60%) |
| identity-service | ~58% | ~45% | MARGINAL (below 60%) |

Note: gateway-service and identity-service coverage is below threshold. These services have limited unit test coverage because their logic is heavily integration-dependent (Redis session management, LDAP lookups). Coverage improvement is a pending task.

---

## SonarQube Analysis

Quality gate: pass

| Metric | Value | Threshold | Status |
|--------|-------|-----------|--------|
| New bugs | 0 | 0 | PASS |
| New vulnerabilities | 0 | 0 | PASS |
| New code smells | < 5 | — | INFO |
| New security hotspots | 2 | review required | REVIEWED |
| Coverage on new code | 62% | 60% | PASS |
| Duplicated lines | < 3% | 3% | PASS |

Security hotspots: both flagged as "not a security issue" after review (standard Spring Security token handling patterns).

---

## Trivy Vulnerability Scan

Scan mode: report-only (non-blocking, `--exit-code 0`)

| Image | HIGH CVEs | CRITICAL CVEs | Root Cause |
|-------|-----------|--------------|-----------|
| All 8 images | 8–12 each | 2–4 each | Spring Boot 3.2.4: Tomcat 10.1.19 (CVE-2025-24813), Spring Security 6.2.3 (CVE-2024-38821) |

Remediation plan: Upgrade Spring Boot platform version to 3.2.12+ across all service `build.gradle.kts` files. No new CVEs introduced by CircleGuard application code.

---

## Performance Tests (Locust)

Test configuration: 10 concurrent users, 1 minute ramp-up, 5 minutes steady state.
Target: stage environment (`circleguard-stage` namespace).

| Endpoint | Requests | RPS | Median (ms) | p95 (ms) | Failure Rate |
|----------|---------|-----|------------|---------|-------------|
| POST /api/auth/login | 412 | 4.2 | 185 | 320 | 0% |
| GET /api/dashboard/hotspots | 389 | 4.0 | 210 | 395 | 0% |
| POST /api/surveys | 378 | 3.8 | 245 | 480 | 0% |
| GET /api/gateway/validate | 420 | 4.3 | 95 | 190 | 0% |
| GET /api/surveys/pending | 350 | 3.6 | 230 | 560 | 0% |
| GET /api/promotion/status | 368 | 3.8 | 190 | 385 | 0% |
| **Total** | **2,558** | **21.77** | **230** | **450** | **0%** |

Known issue: GET /api/surveys/pending shows higher p95 latency (~560ms) due to a complex DB query without an index on the `status` column. Optimization is a pending task.

---

## Security Tests (OWASP ZAP Baseline)

Scan target: dev environment public endpoints via Istio ingress (`http://34.58.31.128`).

| Risk Level | Count | Examples |
|-----------|-------|---------|
| HIGH | 0 | — |
| MEDIUM | 2 | Missing `X-Content-Type-Options` header, `X-Frame-Options` not set |
| LOW | 4 | Cookie `SameSite` attribute not set, `Cache-Control` header missing |
| INFORMATIONAL | 6 | Server banner exposure, non-standard HTTP methods allowed |

All medium findings are missing HTTP security headers. These can be added at the Istio Envoy layer without changing service code. Planned for Phase 8 hardening sprint.

No HIGH or CRITICAL security findings.
