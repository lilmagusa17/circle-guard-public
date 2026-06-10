#!/usr/bin/env bash
# Install kube-prometheus-stack in monitoring namespace.
# Run once per cluster. Requires helm and kubectl configured.
set -euo pipefail
NAMESPACE=monitoring
KUBECONFIG_FILE="${KUBECONFIG:-$HOME/.kube/config}"

kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
    --namespace ${NAMESPACE} \
    --values k8s/monitoring/kube-prometheus-values.yaml \
    --wait \
    --timeout=10m \
    --kubeconfig="${KUBECONFIG_FILE}"

echo "kube-prometheus-stack installed in namespace: ${NAMESPACE}"
echo "Access Grafana: kubectl port-forward svc/kube-prometheus-stack-grafana 3000:80 -n ${NAMESPACE}"
echo "Default Grafana credentials: admin / circleguard-grafana"
