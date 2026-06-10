# CircleGuard Data Flow Diagrams

Key data flows through the system.

---

## Flow 1: User Login and JWT Issuance

```mermaid
sequenceDiagram
    actor User as User (Browser/App)
    participant GW as gateway-service :8087
    participant AUTH as auth-service :8180
    participant REDIS as Redis :6379
    participant LDAP as LDAP / Local DB

    User->>GW: POST /api/auth/login {username, password}
    GW->>AUTH: POST /login (mTLS) {username, password}
    AUTH->>LDAP: Bind query (LDAP) or local DB lookup
    LDAP-->>AUTH: User record
    AUTH-->>AUTH: Generate JWT (HS256, exp: 1h)
    AUTH-->>GW: 200 OK {token, refreshToken}
    GW->>REDIS: SET session:{token_hash} = user_id (TTL: 1h)
    GW-->>User: 200 OK {token, refreshToken}

    Note over User,GW: Subsequent requests include Authorization: Bearer {token}

    User->>GW: GET /api/dashboard/hotspots (Authorization: Bearer {token})
    GW->>REDIS: GET session:{token_hash} → validate
    GW->>AUTH: POST /validate {token} (mTLS)
    AUTH-->>GW: 200 OK {userId, roles}
    GW->>+DASH: GET /hotspots (mTLS, X-User-Id header)
    DASH-->>-GW: 200 OK {hotspots[]}
    GW-->>User: 200 OK {hotspots[]}
```

---

## Flow 2: Health Survey Submission and Notification

```mermaid
sequenceDiagram
    actor User as User
    participant GW as gateway-service :8087
    participant FORM as form-service :8086
    participant PG as PostgreSQL
    participant KAFKA as Kafka
    participant NOTIF as notification-service :8082
    participant PROMO as promotion-service :8088
    participant NEO4J as Neo4j

    User->>GW: POST /api/surveys {symptoms[], userId}
    GW->>FORM: POST /surveys (mTLS)
    FORM->>PG: INSERT survey record (circleguard_form)
    FORM->>KAFKA: Produce → survey.submitted {surveyId, userId, symptoms[]}
    FORM-->>GW: 202 Accepted {surveyId}
    GW-->>User: 202 Accepted {surveyId}

    KAFKA-->>NOTIF: Consume survey.submitted
    NOTIF->>User: Email "Survey received — we will notify you of results"

    KAFKA-->>PROMO: (if high-risk symptoms) Evaluate promotion
    PROMO->>NEO4J: MATCH contact graph (14-day window)
    NEO4J-->>PROMO: Contact list [{userId, contactDepth}]
    PROMO->>PG: UPDATE health_status (circleguard_promotion)
    PROMO->>KAFKA: Produce → promotion.status.changed {userId, newStatus: SUSPECT}
    KAFKA-->>NOTIF: Consume promotion.status.changed
    NOTIF->>User: Email/Push "Health status updated: Suspect"
    PROMO->>KAFKA: Produce → circle.fenced {circleId, affectedUsers[]}
    KAFKA-->>NOTIF: Consume circle.fenced
    NOTIF->>User: Email to affected contacts "You may have been exposed"
```

---

## Flow 3: Certificate Upload and Validation

```mermaid
sequenceDiagram
    actor HC as Health Center Staff
    participant GW as gateway-service :8087
    participant FILE as file-service :8085
    participant KAFKA as Kafka
    participant PROMO as promotion-service :8088

    HC->>GW: POST /api/files/upload {certificate, userId}
    GW->>FILE: POST /upload (mTLS)
    FILE->>FILE: Store to S3-compatible storage
    FILE->>KAFKA: Produce → certificate.validated {userId, certId, result: CONFIRMED}
    FILE-->>GW: 200 OK {fileId}
    GW-->>HC: 200 OK {fileId}

    KAFKA-->>PROMO: Consume certificate.validated
    PROMO->>NEO4J: Mark userId as CONFIRMED in graph
    PROMO->>PROMO: Trigger cascade promotion for contacts
    PROMO->>KAFKA: Produce → promotion.status.changed {userId, newStatus: CONFIRMED}
```

---

## Flow 4: Campus Entry QR Validation

```mermaid
sequenceDiagram
    actor Student as Student (Mobile App)
    participant GW as gateway-service :8087
    participant REDIS as Redis :6379
    participant PROMO as promotion-service :8088

    Note over Student: App generates QR code from stored JWT

    Student->>GW: POST /api/gateway/validate {qrToken}
    GW->>REDIS: GET qr:{token_hash} → check cache
    alt Cache hit
        REDIS-->>GW: {userId, status, expiry}
    else Cache miss
        GW->>PROMO: GET /status/{userId} (mTLS)
        PROMO-->>GW: {userId, status: HEALTHY/SUSPECT/CONFIRMED}
        GW->>REDIS: SET qr:{token_hash} = {userId, status} (TTL: 5min)
    end
    alt Status is HEALTHY
        GW-->>Student: 200 OK — Entry permitted
    else Status is SUSPECT or CONFIRMED
        GW-->>Student: 403 Forbidden — Entry denied, see Health Center
    end
```
