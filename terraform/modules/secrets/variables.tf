variable "project_id" {
  type        = string
  description = "GCP project ID"
}

variable "environment" {
  type        = string
  description = "Environment name (dev, stage, prod)"
}

variable "secrets" {
  type        = map(string)
  description = "Map of secret_id → description. No values stored here."
}
