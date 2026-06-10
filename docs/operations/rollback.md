# Rollback Runbook — CircleGuard

This document contains exact commands for rolling back any service in any environment. Run these commands from a terminal with a valid kubeconfig pointing to the target cluster.

---

## Quick Reference

```bash
# Set context (adjust for target env)
kubectl config use-context circleguard-prod  # or circleguard-dev, circleguard-stage

# Roll back a single service:
kubectl rollout undo deployment/<service-name> -n circleguard-production

# Check rollback status:
kubectl rollout status deployment/<service-name> -n circleguard-production --timeout=120s
```

---

## Service Rollback

Roll back any individual service to its previous ReplicaSet (one version back):

```bash
# auth-service
kubectl rollout undo deployment/auth-service -n circleguard-production
kubectl rollout status deployment/auth-service -n circleguard-production --timeout=120s

# dashboard-service
kubectl rollout undo deployment/dashboard-service -n circleguard-production
kubectl rollout status deployment/dashboard-service -n circleguard-production --timeout=120s

# file-service
kubectl rollout undo deployment/file-service -n circleguard-production
kubectl rollout status deployment/file-service -n circleguard-production --timeout=120s

# form-service
kubectl rollout undo deployment/form-service -n circleguard-production
kubectl rollout status deployment/form-service -n circleguard-production --timeout=120s

# gateway-service
kubectl rollout undo deployment/gateway-service -n circleguard-production
kubectl rollout status deployment/gateway-service -n circleguard-production --timeout=120s

# identity-service
kubectl rollout undo deployment/identity-service -n circleguard-production
kubectl rollout status deployment/identity-service -n circleguard-production --timeout=120s

# notification-service
kubectl rollout undo deployment/notification-service -n circleguard-production
kubectl rollout status deployment/notification-service -n circleguard-production --timeout=120s

# promotion-service
kubectl rollout undo deployment/promotion-service -n circleguard-production
kubectl rollout status deployment/promotion-service -n circleguard-production --timeout=120s
```

To roll back to a specific revision (not just the previous one):
```bash
# List revision history
kubectl rollout history deployment/<service-name> -n circleguard-production

# Roll back to a specific revision number
kubectl rollout undo deployment/<service-name> -n circleguard-production --to-revision=<N>
```

---

## Canary Rollback (Istio VirtualService)

If a canary is active (e.g., auth-service v2 at 10%) and needs immediate rollback:

### Step 1 — Revert VirtualService to 100% v1

```bash
kubectl patch virtualservice auth-service \
    -n circleguard-production \
    --type=merge \
    -p '{"spec":{"http":[{"retries":{"attempts":3,"perTryTimeout":"2s","retryOn":"5xx,gateway-error,connect-failure,retriable-4xx"},"route":[{"destination":{"host":"auth-service","subset":"v1"},"weight":100}]}]}}'
```

Verify the patch applied:
```bash
kubectl get virtualservice auth-service -n circleguard-production -o jsonpath='{.spec.http[0].route}'
# Expected: [{"destination":{"host":"auth-service","subset":"v1"},"weight":100}]
```

### Step 2 — Delete Canary Deployment

```bash
kubectl delete deployment auth-service-canary -n circleguard-production --ignore-not-found
```

### Step 3 — Verify Traffic Back to Stable

```bash
kubectl get pods -n circleguard-production -l app=auth-service
# Only v1 pods should be Running; no canary pods
```

---

## Rolling Back in Dev and Stage

Replace `circleguard-production` with the target namespace:

```bash
# Dev rollback
kubectl rollout undo deployment/<service-name> -n circleguard-dev

# Stage rollback
kubectl rollout undo deployment/<service-name> -n circleguard-stage
```

---

## Infrastructure Rollback (Terraform)

If a Terraform change needs to be reverted:

```bash
# Check the previous state
cd terraform/envs/production
git log --oneline terraform.tfvars  # find the last known-good commit

# Revert the specific file and reapply
git checkout <COMMIT_SHA> -- terraform.tfvars
terraform plan   # verify the diff is what you expect
terraform apply -auto-approve
```

For destructive Terraform changes (e.g., accidentally deleted node pool):
```bash
terraform import module.gke.google_container_node_pool.nodes \
    projects/tallerfinal-496702/locations/us-central1/clusters/circleguard-prod/nodePools/default-pool
terraform plan  # verify state matches reality
```

---

## Production Rollback Drill (documented 2026-06-09)

A rollback drill was performed to validate the procedure and measure timing.

**Scenario**: Intentional deployment of a broken image tag for auth-service.

**Steps taken**:
```bash
# 1. Deploy broken image (simulate bad release)
kubectl set image deployment/auth-service \
    auth-service=magusa17/circleguard-auth:broken-test \
    -n circleguard-production

# 2. Observe failure (tcpSocket probes fail within ~90s)
kubectl get pods -n circleguard-production -w
# auth-service enters CrashLoopBackOff / probe failures

# 3. Trigger rollback
kubectl rollout undo deployment/auth-service -n circleguard-production

# 4. Verify
kubectl rollout status deployment/auth-service -n circleguard-production --timeout=120s
```

**Results**:
- Time from identifying issue to initiating rollback: ~2 minutes (manual observation)
- Time from `kubectl rollout undo` to `Running` status: ~58 seconds
- Total downtime for auth-service: ~3 minutes
- Impact: requests to auth endpoints failed with 503 from Istio during the window; other services unaffected

**Lesson**: Set up Alertmanager alert for CrashLoopBackOff so rollback can be initiated in under 1 minute of detection, not 2.

---

## Decision Tree

```
Service issue detected
        ↓
Is the canary still active (< 100% traffic)?
  YES → Deny canary gate in Jenkins (auto-rollback) OR follow Canary Rollback steps above
  NO  ↓
Is the issue a bad container image?
  YES → kubectl rollout undo deployment/<svc> -n circleguard-production
  NO  ↓
Is the issue a bad configuration (ConfigMap/Secret)?
  YES → Edit the ConfigMap/Secret, then: kubectl rollout restart deployment/<svc> -n circleguard-production
  NO  ↓
Is the issue infrastructure (node failure, network)?
  YES → Check GKE node status: kubectl get nodes
        If node is NotReady: GKE auto-healer will replace it (watch kubectl get nodes -w)
        If persistent: gcloud container clusters resize ... or terraform apply
```
