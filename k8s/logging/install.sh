#!/usr/bin/env bash
# Install ELK stack for CircleGuard log aggregation.
set -euo pipefail
NAMESPACE=logging
KUBECONFIG_FILE="${KUBECONFIG:-$HOME/.kube/config}"

kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -

helm repo add elastic https://helm.elastic.co
helm repo add fluent https://fluent.github.io/helm-charts
helm repo update

# Elasticsearch
helm upgrade --install elasticsearch elastic/elasticsearch \
    --namespace ${NAMESPACE} \
    --values k8s/logging/elasticsearch-kibana-values.yaml \
    --set nameOverride=elasticsearch \
    --wait --timeout=15m \
    --kubeconfig="${KUBECONFIG_FILE}"

# Kibana
helm upgrade --install kibana elastic/kibana \
    --namespace ${NAMESPACE} \
    --set elasticsearchHosts="http://elasticsearch-master:9200" \
    --set resources.requests.memory=512Mi \
    --set resources.limits.memory=1Gi \
    --wait --timeout=10m \
    --kubeconfig="${KUBECONFIG_FILE}"

# Fluent Bit
helm upgrade --install fluent-bit fluent/fluent-bit \
    --namespace ${NAMESPACE} \
    --values k8s/logging/fluent-bit-values.yaml \
    --wait --timeout=5m \
    --kubeconfig="${KUBECONFIG_FILE}"

echo "ELK stack installed in namespace: ${NAMESPACE}"
echo "Access Kibana: kubectl port-forward svc/kibana-kibana 5601:5601 -n ${NAMESPACE}"
echo "Create index pattern: circleguard-* in Kibana > Stack Management > Index Patterns"
