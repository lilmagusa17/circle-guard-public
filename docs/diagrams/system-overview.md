# CircleGuard System Overview

Architecture diagram showing all 8 microservices, their ports, Kafka event flows, and data stores.

---

```mermaid
---
title: CircleGuard System Overview
---
graph TB
    subgraph External["External"]
        Client["Browser / Mobile App"]
    end

    subgraph GKE["GKE Cluster — circleguard-*"]
        subgraph Ingress["Istio Ingress Gateway  :80 / :443"]
            GW["gateway-service :8087"]
        end

        subgraph Services["Microservices (Istio mTLS mesh)"]
            AUTH["auth-service :8180"]
            DASH["dashboard-service :8084"]
            FILE["file-service :8085"]
            FORM["form-service :8086"]
            ID["identity-service :8083"]
            NOTIF["notification-service :8082"]
            PROMO["promotion-service :8088"]
        end

        subgraph Infrastructure["Infrastructure (outside mesh)"]
            PG[("PostgreSQL :5432")]
            KAFKA["Kafka :9092"]
            REDIS[("Redis :6379")]
            NEO4J[("Neo4j :7687")]
        end
    end

    Client -->|"HTTP / HTTPS"| GW

    GW -->|"mTLS — JWT validation"| AUTH
    GW -->|"mTLS — analytics"| DASH
    GW -->|"mTLS — certificates"| FILE
    GW -->|"mTLS — health forms"| FORM
    GW -->|"mTLS — identity vault"| ID
    GW -->|"mTLS — notifications"| NOTIF
    GW -->|"mTLS — health status"| PROMO

    AUTH -->|"SQL"| PG
    DASH -->|"SQL"| PG
    FORM -->|"SQL"| PG
    ID -->|"SQL"| PG
    PROMO -->|"SQL"| PG

    FORM -->|"produce: survey.submitted"| KAFKA
    PROMO -->|"produce: promotion.status.changed, circle.fenced"| KAFKA
    KAFKA -->|"consume: alert.priority, survey.submitted"| NOTIF
    KAFKA -->|"consume: certificate.validated"| PROMO

    GW -->|"session cache — QR tokens"| REDIS
    PROMO -->|"health graph traversal"| NEO4J
    PROMO -->|"status cache"| REDIS

    DASH -->|"status queries"| PROMO
```

---

## Service Descriptions

| Service | Port | Responsibility | Data Store |
|---------|------|---------------|-----------|
| `gateway-service` | 8087 | API gateway — QR code validation, Redis-backed sessions, JWT routing | Redis |
| `auth-service` | 8180 | JWT authentication, LDAP integration | PostgreSQL (`circleguard_auth`) |
| `dashboard-service` | 8084 | Geospatial hotspot analytics with k-anonymity privacy filter | PostgreSQL (`circleguard_dashboard`) |
| `file-service` | 8085 | Secure certificate and document storage (S3-compatible) | PostgreSQL (`circleguard_form`) |
| `form-service` | 8086 | Health survey forms, Kafka producer for survey events | PostgreSQL (`circleguard_form`) |
| `identity-service` | 8083 | Identity vault management, FERPA-compliant anonymization | PostgreSQL (`circleguard_identity`) |
| `notification-service` | 8082 | Email and alert notifications, Kafka consumer | None (stateless) |
| `promotion-service` | 8088 | Health status lifecycle management, contact graph traversal | Neo4j, Redis, PostgreSQL (`circleguard_promotion`) |

---

## Kafka Topics

| Topic | Producer | Consumer |
|-------|---------|---------|
| `survey.submitted` | form-service | notification-service |
| `alert.priority` | promotion-service | notification-service |
| `circle.fenced` | promotion-service | notification-service |
| `promotion.status.changed` | promotion-service | notification-service |
| `certificate.validated` | file-service | promotion-service |
