variable "project_id" {
  type        = string
  description = "GCP project ID"
}

variable "environment" {
  type        = string
  description = "Environment name (dev, stage, prod)"
}

variable "k8s_namespace" {
  type        = string
  description = "Kubernetes namespace where microservices run"
}
