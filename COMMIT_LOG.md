# Commit Log — Proyecto Final IngeSoft V
> Registro del proceso de implementación para documentación y sustentación.

---

## Commit 1 — `feat/agile-setup`
**Fecha:** 2026-06-03
**Branch:** `feat/agile-setup` → merge a `master`
**Mensaje de commit:** `docs: add agile board setup and GitHub Flow branching strategy`

### Qué se hizo
- Creado `docs/agile.md`: tablero GitHub Projects, 10 historias de usuario con criterios de aceptación, definición de Sprint 1 (Infraestructura) y Sprint 2 (Calidad y Observabilidad).
- Creado `docs/branching.md`: documentación completa de GitHub Flow — convención de nombres de ramas, mapeo a pipelines Jenkins, reglas de protección de `master`, y estándar de Conventional Commits para SemVer automático.

### Por qué
El enunciado exige metodología ágil documentada con sprints e historias de usuario (10% de la nota). Estos archivos son evidencia directa de ese requisito. La estrategia de branching también es necesaria para que los pipelines de Jenkins sepan qué hacer en cada rama.

### Archivos creados
- `docs/agile.md`
- `docs/branching.md`

---

## Commit 2 — `feat/ci-improvements`
**Fecha:** 2026-06-03
**Branch:** `feat/ci-improvements` → merge a `master`
**Mensaje de commit:** `ci: update Jenkins image with preinstalled tools and docker proxy`

### Qué se hizo
- **`jenkins/Dockerfile.jenkins` reescrito:** Se reemplazó el Docker CLI básico y minikube por una imagen mejorada que incluye: Docker CE CLI + Buildx, GitHub CLI (`gh`), Trivy (escáner de vulnerabilidades), Locust (preinstalado para eliminar ~2min de delay por build), y el docker-version-proxy. También se añadieron los plugins `sonar` y `discord-notifier`.
- **`jenkins/docker-version-proxy.py` añadido:** Proxy Unix socket que reescribe paths de la Docker API de `/v1.32/` a `/v1.44/`. Necesario porque `docker-java` (usado por Testcontainers) hardcodea rutas antiguas y Docker 29.x exige mínimo API 1.44. Sin este proxy, los tests de integración fallan.
- **`jenkins/jenkins-setup.sh` añadido:** Script de inicialización para un servidor Jenkins en GCP (cloud-init). Instala Java 21, Jenkins, Docker, kubectl, `gcloud` SDK + `gke-gcloud-auth-plugin`, Trivy, Locust y GitHub CLI. Reemplaza `doctl` (DigitalOcean) por `gcloud` ya que la infraestructura es GKE.

### Por qué
El Dockerfile anterior instalaba `minikube` (irrelevante en GKE) y no tenía Trivy, Locust ni el docker-version-proxy. Estos tres son necesarios para los pipelines avanzados del Proyecto Final: Trivy para el escaneo de imágenes (requerido en CI/CD 15%), Locust para pruebas de rendimiento (requerido en Pruebas 15%), y el proxy para que Testcontainers funcione correctamente dentro del contenedor Jenkins.

### Archivos modificados/creados
- `jenkins/Dockerfile.jenkins` (modificado)
- `jenkins/docker-version-proxy.py` (nuevo)
- `jenkins/jenkins-setup.sh` (nuevo)

---

## Commit 4 — `feat/terraform-environments`
**Fecha:** 2026-06-03
**Branch:** `feat/terraform-environments` → merge a `master`
**Mensaje de commit:** `feat(terraform): add dev/stage/prod environments with remote GCS backend`

### Qué se hizo
- Creados `terraform/envs/dev/`, `terraform/envs/stage/`, `terraform/envs/prod/` — cada uno con `backend.tf`, `main.tf`, `provider.tf`, `variables.tf`, `terraform.tfvars`.
- **Project ID actualizado:** `tallerfinal-496702` → `circleguard-final` en los 3 `terraform.tfvars`.
- **Bucket de estado actualizado:** `circle-guard-tfstate-496702` → `circle-guard-tfstate-final` en los 3 `backend.tf`.
- `terraform/README.md` añadido: instrucciones de primer arranque (crear el bucket GCS manualmente antes de `terraform init`), comandos de apply/destroy por ambiente, tabla de red y advertencia de cuota de CPUs.

### Por qué
El enunciado exige Terraform modular para múltiples ambientes (20% de la nota). Estos archivos son la configuración ejecutable de los tres clusters GKE. El único cambio respecto al repo de  son el project ID y el nombre del bucket — todo lo demás (sizing, redes, módulos) es idéntico y funcional.

### Paso previo necesario (antes de `terraform init`)
Crear el bucket GCS de estado remoto manualmente **una sola vez**:
```bash
gcloud config set project circleguard-final
gsutil mb -l us-central1 gs://circle-guard-tfstate-final
gsutil versioning set on gs://circle-guard-tfstate-final
```

### ⚠️ Nota sobre Commit 3
Los módulos son genéricos y no contienen referencias al project ID.

### Archivos creados
- `terraform/envs/dev/` (5 archivos)
- `terraform/envs/stage/` (5 archivos)
- `terraform/envs/prod/` (5 archivos)
- `terraform/README.md`

---

<!-- Los siguientes commits se irán añadiendo aquí a medida que se completen -->
