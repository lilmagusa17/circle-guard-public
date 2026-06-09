# Existing Design Patterns in CircleGuard

Patterns found in the codebase as of Phase 5 audit. Each entry includes the pattern name, where it is used, and why it was chosen.

---

## 1. API Gateway

**Service:** `circleguard-gateway-service` (port 8087)  
**Files:** [`services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/controller/GateController.java`](../../services/circleguard-gateway-service/src/main/java/com/circleguard/gateway/controller/GateController.java)

The gateway service is the single external entry point for all client requests. It validates QR codes, manages Redis-backed sessions, and verifies JWT tokens before forwarding requests downstream. Clients never call microservices directly.

**Why:** Reduces coupling between clients and internal services. Centralizes cross-cutting concerns (auth, rate limiting) at the edge without duplicating them in every service.

**Benefit:** Internal services can be refactored or renamed without changing client contracts. Adding authentication to a new service means updating the gateway only.

---

## 2. Database per Service

**Services:** All 8 microservices  
**Databases:**
- `circleguard_auth` → auth-service
- `circleguard_dashboard` → dashboard-service
- `circleguard_form` → form-service
- `circleguard_identity` → identity-service
- `circleguard_promotion` → promotion-service (also uses Neo4j + Redis)

Each service owns its database schema and applies migrations via Flyway. No service directly queries another service's tables.

**Why:** Enables independent deployment and scaling. The promotion-service can use Neo4j for graph queries while dashboard-service uses PostgreSQL relational queries — no shared schema.

**Benefit:** Services can be scaled, upgraded, or replaced independently. A schema change in one service does not require coordination with all other services.

---

## 3. Event-Driven Architecture (Kafka)

**Producer:** `circleguard-form-service` — publishes `survey.submitted` events on health form submission  
**Consumers:** `circleguard-notification-service`, `circleguard-promotion-service`  
**Files:**
- [`services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/PriorityAlertListener.java`](../../services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/PriorityAlertListener.java)
- [`services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/ExposureNotificationListener.java`](../../services/circleguard-notification-service/src/main/java/com/circleguard/notification/service/ExposureNotificationListener.java)
- [`services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/listener/SurveyListener.java`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/listener/SurveyListener.java)

**Topics:** `alert.priority`, `survey.submitted`, `circle.fenced`, `promotion.status.changed`, `certificate.validated`

**Why:** Decouples producers from consumers. The form-service does not know that notification-service or promotion-service exist. Adding a new consumer (e.g., an analytics service) requires zero changes to the producer.

**Benefit:** Services remain loosely coupled. A notification-service failure does not cause form submissions to fail — Kafka buffers the events until the consumer recovers.

---

## 4. JWT Authentication / Token-Based Security

**Service:** `circleguard-auth-service`  
**Files:**
- [`services/circleguard-auth-service/src/main/java/com/circleguard/auth/service/JwtTokenService.java`](../../services/circleguard-auth-service/src/main/java/com/circleguard/auth/service/JwtTokenService.java)
- [`services/circleguard-auth-service/src/main/java/com/circleguard/auth/security/JwtAuthenticationFilter.java`](../../services/circleguard-auth-service/src/main/java/com/circleguard/auth/security/JwtAuthenticationFilter.java)

The auth-service issues signed JWT tokens after validating credentials against LDAP. The gateway-service and other services validate the JWT signature without calling auth-service on every request (stateless verification).

**Why:** Stateless token verification means no session lookup on every request — each service can verify the token independently using the shared secret.

**Benefit:** Low latency authorization. No single point of failure for session state.

---

## 5. k-Anonymity Privacy Filter

**Service:** `circleguard-dashboard-service`  
**Files:**
- [`services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/service/KAnonymityFilter.java`](../../services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/service/KAnonymityFilter.java)
- [`services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/service/AnalyticsService.java`](../../services/circleguard-dashboard-service/src/main/java/com/circleguard/dashboard/service/AnalyticsService.java)

Before returning geospatial hotspot data, the dashboard service applies k-anonymity suppression: any group of fewer than k individuals is suppressed from the result set to prevent re-identification.

**Why:** Health data is sensitive. Reporting the exact location of a single sick individual would violate privacy. k-anonymity is an established privacy model (Sweeney 2002) that is both explainable and auditable.

**Benefit:** Regulatory compliance (GDPR, Colombian personal data law). Reduces liability if the analytics data is accessed by unauthorized parties.

---

## 6. Repository Pattern (Spring Data JPA)

**Services:** auth-service, dashboard-service, form-service, identity-service, promotion-service  
**Example:** `services/*/src/main/java/com/circleguard/*/repository/`

All services using PostgreSQL define interfaces extending `JpaRepository<Entity, ID>`. Queries are expressed as method names or `@Query` annotations; no JDBC/SQL boilerplate in service classes.

**Why:** Decouples business logic from persistence technology. Swapping PostgreSQL for another database would require only changing the dependency, not the service code.

**Benefit:** Faster development, type-safe queries, automatic pagination/sorting.

---

## 7. Health Status State Machine

**Service:** `circleguard-promotion-service`  
**Files:**
- [`services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/service/HealthStatusService.java)
- [`services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/listener/SurveyListener.java`](../../services/circleguard-promotion-service/src/main/java/com/circleguard/promotion/listener/SurveyListener.java)

The health lifecycle (Healthy → At Risk → Quarantined → Recovered) is modeled as a state machine stored in Neo4j. Transitions are triggered by Kafka events from form-service. Redis is used to cache current state for low-latency reads.

**Why:** Graph database (Neo4j) naturally represents relationship chains — "person A was in proximity to person B who is sick". Redis caching avoids expensive graph traversals on every status check.

**Benefit:** Contact tracing queries that would be complex SQL joins become simple graph traversals. Cache-aside pattern reduces p95 latency from ~200ms to ~5ms for status lookups.
