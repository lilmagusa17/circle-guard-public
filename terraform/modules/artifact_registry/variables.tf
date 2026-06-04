variable "project_id" {
  type        = string
  description = "GCP project ID"
}

variable "location" {
  type        = string
  description = "Artifact Registry location (e.g. us-central1)"
}

variable "repository_id" {
  type        = string
  description = "Repository name (e.g. circleguard)"
}
