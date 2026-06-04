variable "project_id" {
  type        = string
  description = "GCP project ID"
}

variable "region" {
  type        = string
  description = "GCP region"
}

variable "name" {
  type        = string
  description = "VPC name prefix (e.g. circleguard-dev)"
}

variable "subnet_cidr" {
  type        = string
  description = "Node subnet CIDR"
}

variable "pods_cidr" {
  type        = string
  description = "Secondary range CIDR for GKE pods"
}

variable "services_cidr" {
  type        = string
  description = "Secondary range CIDR for GKE services"
}
