# Jenkins Setup Guide - CircleGuard

## 1. Iniciar Jenkins

```bash
cd circle-guard-public/jenkins
docker-compose -f docker-compose.jenkins.yml up -d
```

Accede en: http://localhost:8090

Para obtener la contraseña inicial:
```bash
docker exec circleguard-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

---

## 2. Instalar Plugins

En Jenkins UI → **Manage Jenkins → Plugins → Available plugins**, instala:

- Pipeline
- Git
- Docker Pipeline
- Kubernetes CLI
- JUnit
- JaCoCo
- Blue Ocean (opcional, para UI mejorada)

Reinicia Jenkins tras instalar.

---

## 3. Configurar JDK 21

**Manage Jenkins → Tools → JDK → Add JDK**

- Name: `JDK-21`
- JAVA_HOME: `/opt/java/openjdk`  
  *(o usa "Install automatically" → adoptium 21)*

---

## 4. Registrar la Shared Library

**Manage Jenkins → System → Global Pipeline Libraries → Add**

| Campo | Valor |
|---|---|
| Name | `circleguard-shared-lib` |
| Default version | `main` |
| Source Code Management | Git |
| Project Repository | `https://github.com/TU_ORG/circle-guard-public` |
| Library path | `jenkins/shared-library` |

> Si el repo es privado, agrega credenciales Git aquí.

---

## 5. Configurar Credenciales

**Manage Jenkins → Credentials → (global) → Add Credentials**

### Docker Registry
- Kind: **Username with password**
- ID: `docker-registry-credentials`
- Username: tu usuario de Docker Hub
- Password: tu token de Docker Hub

### Kubeconfig
- Kind: **Secret file**
- ID: `kubeconfig`
- File: sube el archivo `~/.kube/config` de tu máquina

---

## 6. Crear los 18 Pipeline Jobs

Por cada combinación servicio × ambiente, crea un Pipeline job.

### Pasos para crear un job:

1. **New Item** → Nombre: `form-service-dev` → tipo: **Pipeline**
2. En **Pipeline section**:
   - Definition: **Pipeline script from SCM**
   - SCM: **Git**
   - Repository URL: `https://github.com/TU_ORG/circle-guard-public`
   - Script Path: `services/circleguard-form-service/Jenkinsfile.dev`
3. **Save**

### Lista completa de jobs:

| Job Name | Script Path |
|---|---|
| `file-service-dev` | `services/circleguard-file-service/Jenkinsfile.dev` |
| `file-service-stage` | `services/circleguard-file-service/Jenkinsfile.stage` |
| `file-service-master` | `services/circleguard-file-service/Jenkinsfile.master` |
| `form-service-dev` | `services/circleguard-form-service/Jenkinsfile.dev` |
| `form-service-stage` | `services/circleguard-form-service/Jenkinsfile.stage` |
| `form-service-master` | `services/circleguard-form-service/Jenkinsfile.master` |
| `gateway-service-dev` | `services/circleguard-gateway-service/Jenkinsfile.dev` |
| `gateway-service-stage` | `services/circleguard-gateway-service/Jenkinsfile.stage` |
| `gateway-service-master` | `services/circleguard-gateway-service/Jenkinsfile.master` |
| `identity-service-dev` | `services/circleguard-identity-service/Jenkinsfile.dev` |
| `identity-service-stage` | `services/circleguard-identity-service/Jenkinsfile.stage` |
| `identity-service-master` | `services/circleguard-identity-service/Jenkinsfile.master` |
| `notification-service-dev` | `services/circleguard-notification-service/Jenkinsfile.dev` |
| `notification-service-stage` | `services/circleguard-notification-service/Jenkinsfile.stage` |
| `notification-service-master` | `services/circleguard-notification-service/Jenkinsfile.master` |
| `promotion-service-dev` | `services/circleguard-promotion-service/Jenkinsfile.dev` |
| `promotion-service-stage` | `services/circleguard-promotion-service/Jenkinsfile.stage` |
| `promotion-service-master` | `services/circleguard-promotion-service/Jenkinsfile.master` |

---

## 7. Kubernetes (Minikube)

```bash
# Iniciar Minikube
minikube start --driver=docker

# Crear namespaces
kubectl apply -f k8s/namespaces.yaml

# Infra DEV
kubectl apply -f k8s/infra/postgres.yaml
kubectl apply -f k8s/infra/kafka.yaml
kubectl apply -f k8s/infra/redis.yaml

# Verificar
kubectl get pods -n circleguard-dev
```

---

## 8. Ejecutar los Pipelines

**Orden recomendado:**
1. `identity-service-dev` (no tiene dependencias)
2. `notification-service-dev`
3. `file-service-dev`
4. `gateway-service-dev`
5. `promotion-service-dev`
6. `form-service-dev` (orquestador principal)

Para stage/master, primero aplica la infra correspondiente:
```bash
kubectl apply -f k8s/infra/postgres-stage.yaml
kubectl apply -f k8s/infra/kafka-stage.yaml
kubectl apply -f k8s/infra/redis-stage.yaml
```

---

## 9. Verificar un Pipeline exitoso

Un pipeline DEV típico ejecuta:
1. ✅ Checkout
2. ✅ Build (`bootJar`)
3. ✅ Unit Tests + JaCoCo
4. ✅ Docker Build & Push
5. ✅ Deploy to `circleguard-dev` namespace

Un pipeline STAGE agrega:
- ✅ Integration Tests
- ✅ E2E Tests

Un pipeline MASTER agrega:
- ✅ Performance Tests (Locust)
- ✅ Approval (manual)
- ✅ Release Notes → `CHANGELOG.md`
