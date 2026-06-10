#!/usr/bin/env bash
# Install cert-manager via Helm and create ClusterIssuer for Let's Encrypt.
set -euo pipefail

helm repo add jetstack https://charts.jetstack.io
helm repo update

kubectl create namespace cert-manager --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install cert-manager jetstack/cert-manager \
    --namespace cert-manager \
    --version v1.14.4 \
    --set installCRDs=true \
    --wait

echo "cert-manager installed. Applying ClusterIssuer..."
kubectl apply -f k8s/security/cluster-issuer.yaml
echo "Done. TLS certificate provisioning active."
