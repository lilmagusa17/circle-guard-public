#!/usr/bin/env bash
# Install Chaos Mesh in the CircleGuard dev cluster via Helm.
# Chaos Mesh is chosen over LitmusChaos for its Grafana-friendly metrics and
# built-in web dashboard, which makes the demo more visual.
#
# Requires: helm, kubectl (context pointing at circleguard-dev), GKE cluster up.
# Docs: https://chaos-mesh.org/docs/production-installation-using-helm/
set -euo pipefail

NAMESPACE="chaos-mesh"
VERSION="2.6.3"   # pin a known-good chart version

echo "=== Installing Chaos Mesh ${VERSION} in namespace ${NAMESPACE} ==="

helm repo add chaos-mesh https://charts.chaos-mesh.org 2>/dev/null || true
helm repo update

# GKE uses containerd; socketPath must point at the containerd socket so
# PodChaos/StressChaos can reach the container runtime.
helm upgrade --install chaos-mesh chaos-mesh/chaos-mesh \
  --namespace "${NAMESPACE}" \
  --create-namespace \
  --version "${VERSION}" \
  --set chaosDaemon.runtime=containerd \
  --set chaosDaemon.socketPath=/run/containerd/containerd.sock \
  --set dashboard.create=true \
  --wait

echo ""
echo "=== Waiting for Chaos Mesh pods to be Running ==="
kubectl wait --for=condition=Ready pods --all -n "${NAMESPACE}" --timeout=180s

echo ""
kubectl get pods -n "${NAMESPACE}"

echo ""
echo "Chaos Mesh installed. To open the dashboard:"
echo "  kubectl port-forward -n ${NAMESPACE} svc/chaos-dashboard 2333:2333"
echo "  then open http://localhost:2333"
echo ""
echo "NOTE: experiments target namespace circleguard-dev. The ChaosExperiment"
echo "manifests in tests/chaos/ already scope to that namespace."
