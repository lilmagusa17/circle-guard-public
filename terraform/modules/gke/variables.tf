variable "project_id" {
  type        = string
  description = "GCP project ID"
}

variable "region" {
  type        = string
  description = "GCP region"
}

variable "cluster_name" {
  type        = string
  description = "GKE cluster name"
}

variable "network_id" {
  type        = string
  description = "VPC network self-link or ID"
}

variable "subnet_id" {
  type        = string
  description = "Subnetwork self-link or ID"
}

variable "pods_range_name" {
  type        = string
  description = "Secondary IP range name for pods"
}

variable "services_range_name" {
  type        = string
  description = "Secondary IP range name for services"
}

variable "node_count" {
  type        = number
  description = "Initial nodes per zone"
  default     = 1
}

variable "min_node_count" {
  type        = number
  description = "Minimum nodes per zone (autoscaling)"
  default     = 0
}

variable "max_node_count" {
  type        = number
  description = "Maximum nodes per zone (autoscaling)"
  default     = 3
}

variable "machine_type" {
  type        = string
  description = "Node machine type"
  default     = "e2-standard-2"
}

variable "use_spot" {
  type        = bool
  description = "Use Spot (preemptible) VMs to reduce cost"
  default     = false
}

variable "disk_size_gb" {
  type        = number
  description = "Boot disk size in GB"
  default     = 50
}
