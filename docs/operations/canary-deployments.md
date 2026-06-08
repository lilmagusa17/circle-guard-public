# Canary Deployment Workflow — gateway-service

CircleGuard uses Istio VirtualService traffic splitting for canary deployments.
The canary is configured on `gateway-service` (the API Gateway) as a representative service.

## Architecture

Two subsets defined in `k8s/istio/dev/destination-rules.yaml`:

```yaml
subsets:
  - name: v1
    labels:
      version: v1   # stable pods
  - name: v2
    labels:
      version: v2   # canary pods
```

VirtualService routes by weight (default: 100% v1, 0% v2).

## Activating a canary

### Step 1 — Deploy canary pods

Deploy a new Deployment for gateway-service with `version: v2` label:

```bash
kubectl apply -f k8s/dev/gateway-service-canary.yaml -n circleguard-dev
```

The canary Deployment must have label `version: v2` on pod template.

### Step 2 — Shift traffic 10% to canary

Edit `k8s/istio/dev/virtual-services.yaml` gateway-service section:

```yaml
route:
  - destination:
      host: gateway-service
      subset: v1
    weight: 90
  - destination:
      host: gateway-service
      subset: v2
    weight: 10
```

Apply: `kubectl apply -f k8s/istio/dev/virtual-services.yaml`

### Step 3 — Monitor in Kiali

```bash
istioctl dashboard kiali --kubeconfig ~/.kube/circleguard-dev
```

Watch the service graph for error rates on v1 vs v2.

### Step 4a — Promote (no errors)

Set v1 weight to 0, v2 weight to 100. Apply. Delete old v1 Deployment.

### Step 4b — Rollback (errors detected)

Set v1 weight to 100, v2 weight to 0. Apply. Delete v2 Deployment.

## Monitoring metrics during canary

Kiali shows per-subset traffic in the service graph.
Key metrics to watch (Grafana, Phase 7):
- Error rate per subset
- p95 latency per subset

A canary promotion gate is implemented in the master pipeline (Phase 4.7):
- Deploy canary at 10%
- Wait for manual approval (30 min observation window)
- Approve → 100%, or rollback → 0%

## Current state

Canary inactive (v1=100, v2=0). No v2 Deployment exists.
