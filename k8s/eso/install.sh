#!/usr/bin/env bash
# Install External Secrets Operator in all three CircleGuard clusters.
# Requires: helm, kubectl, gcloud, USE_GKE_GCLOUD_AUTH_PLUGIN=True
set -euo pipefail

GCP_PROJECT="circleguard-final"
GCP_REGION="us-central1"

install_eso() {
    local cluster="$1"
    local kubeconfig="$HOME/.kube/${cluster}"

    echo "=== Installing ESO on ${cluster} ==="
    export KUBECONFIG="$kubeconfig"

    helm repo add external-secrets https://charts.external-secrets.io 2>/dev/null || true
    helm repo update

    helm upgrade --install external-secrets \
        external-secrets/external-secrets \
        --namespace external-secrets \
        --create-namespace \
        --set installCRDs=true \
        --set webhook.port=9443 \
        --wait

    echo "ESO installed on ${cluster}"
}

# Apply cluster-level resources for each environment
apply_cluster_resources() {
    local cluster="$1"
    local namespace="$2"
    local kubeconfig="$HOME/.kube/${cluster}"
    local env_dir

    case "$cluster" in
        circleguard-dev)   env_dir="dev"        ;;
        circleguard-stage) env_dir="stage"      ;;
        circleguard-prod)  env_dir="production" ;;
    esac

    export KUBECONFIG="$kubeconfig"
    kubectl apply -f "$(dirname "$0")/${env_dir}/"
    echo "Applied ESO resources for ${namespace}"
}

install_eso circleguard-dev
install_eso circleguard-stage
install_eso circleguard-prod

apply_cluster_resources circleguard-dev   circleguard-dev
apply_cluster_resources circleguard-stage circleguard-stage
apply_cluster_resources circleguard-prod  circleguard-production

echo "=== ESO installation complete ==="
