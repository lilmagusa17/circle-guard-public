# CircleGuard – Agile Management

## GitHub Projects Board

**Board:** CircleGuard Proyecto Final
**URL:** https://github.com/users/lilmagusa17/projects/1
**Columns:** Backlog · To Do · In Progress · Review · Done
**Repository:** https://github.com/lilmagusa17/circle-guard-public

---

## User Stories

All user stories are created as GitHub Issues in the fork repository with phase labels.

| ID | Phase | Title | Acceptance Criteria |
|----|-------|-------|---------------------|
| US-01 | Phase 1 | Provision reproducible GCP infrastructure via Terraform | Three GKE clusters (dev/stage/prod) created via `terraform apply`; `kubectl get nodes` succeeds on each |
| US-02 | Phase 2 | Migrate K8s manifests to GKE | All 8 microservices Running in `circleguard-dev`; smoke test passes |
| US-03 | Phase 3 | Secure all inter-service communication with Istio mTLS | `kubectl get peerauthentication -A` shows STRICT mode; Kiali shows lock icons |
| US-04 | Phase 4 | Enhanced CI/CD with quality gates and canary deployments | SonarQube + Trivy stages green; master pipeline performs canary 10%→100% |
| US-05 | Phase 5 | Implement and document design patterns | 3 patterns documented with code references: circuit breaker, external config, sidecar |
| US-06 | Phase 6 | Coverage reports and OWASP ZAP scans in pipeline | JaCoCo coverage ≥ 60% published; ZAP report archived in Jenkins |
| US-07 | Phase 7 | Full observability stack | Prometheus, Grafana, ELK, Jaeger running; dashboards show live data for all 8 services |
| US-08 | Phase 8 | Secrets via GCP Secret Manager, RBAC and TLS | No plaintext secrets in `k8s/`; RBAC enforced per service; public endpoints serve HTTPS |
| US-09 | Phase 9 | Automated release notes and rollback runbooks | GitHub Release created automatically on master pipeline run with parsed notes |
| US-10 | Phase 10 | Complete documentation | New developer can clone, provision, and deploy reading only README.md |

---

## Sprint Definitions

### Sprint 1 — Infrastructure & Deployment

**Goal:** Provision GCP/GKE environment, migrate existing K8s manifests, and establish the service mesh.
**Duration:** 2 weeks (Semana 1–2 del Proyecto Final)
**Scope:** Phases 0, 1, 2, 3

| US | Story Points | Description |
|----|-------------|-------------|
| US-01 | 13 | Terraform modular para GCP |
| US-02 | 8 | Migración de manifests a GKE |
| US-03 | 13 | Istio mTLS + service mesh |

**Sprint 1 Definition of Done:**
- Tres clusters GKE (dev/stage/prod) aprovisionados via Terraform y alcanzables con `kubectl`.
- Los 8 microservicios Running en cada namespace.
- Istio mTLS STRICT enforced; Kiali muestra lock icons en todas las conexiones.
- Script `ci/smoke-test.sh` pasa contra los tres ambientes.

---

### Sprint 2 — Quality, Security & Observability

**Goal:** Hardening del sistema con quality gates, observabilidad, seguridad y documentación final.
**Duration:** 2 weeks (Semana 3–4 del Proyecto Final)
**Scope:** Phases 4, 5, 6, 7, 8, 9, 10

| US | Story Points | Description |
|----|-------------|-------------|
| US-04 | 13 | CI/CD avanzado con canary |
| US-05 | 8 | Patrones de diseño |
| US-06 | 8 | Testing y cobertura |
| US-07 | 13 | Stack de observabilidad |
| US-08 | 8 | Seguridad y RBAC |
| US-09 | 5 | Change management y release notes |
| US-10 | 5 | Documentación final |

**Sprint 2 Definition of Done:**
- Todos los pipelines incluyen SonarQube + Trivy; master pipeline realiza canary deployment.
- Cobertura JaCoCo ≥ 60% publicada en Jenkins; reporte ZAP archivado en pipeline.
- Stack Prometheus/Grafana/ELK/Jaeger corriendo; dashboards con data en vivo para los 8 servicios.
- Sin secretos en texto plano en `k8s/`; RBAC por servicio; TLS en endpoints públicos.
- GitHub Release creado automáticamente con release notes parseadas en cada ejecución del master pipeline.
- `README.md` y todos los entregables de `docs/` completos.
