module "vpc" {
  source        = "../../modules/vpc"
  project_id    = var.project_id
  region        = var.region
  name          = "circleguard-stage"
  subnet_cidr   = "10.20.0.0/24"
  pods_cidr     = "10.20.4.0/22"
  services_cidr = "10.20.8.0/24"
}

module "gke" {
  source              = "../../modules/gke"
  project_id          = var.project_id
  region              = var.region
  cluster_name        = "circleguard-stage"
  network_id          = module.vpc.network_id
  subnet_id           = module.vpc.subnet_id
  pods_range_name     = module.vpc.pods_range_name
  services_range_name = module.vpc.services_range_name
  node_count          = 1
  min_node_count      = 0
  max_node_count      = 3
  machine_type        = "e2-standard-2"
  use_spot            = true
  disk_size_gb        = 50
}

module "secrets" {
  source      = "../../modules/secrets"
  project_id  = var.project_id
  environment = "stage"
  secrets = {
    "cg-db-password-stage"        = "PostgreSQL password for stage"
    "cg-jwt-secret-stage"         = "JWT signing secret for stage"
    "cg-dockerhub-user-stage"     = "Docker Hub username"
    "cg-dockerhub-password-stage" = "Docker Hub password or token"
    "cg-mail-password-stage"      = "SMTP password for notifications"
  }
}

module "iam" {
  source        = "../../modules/iam"
  project_id    = var.project_id
  environment   = "stage"
  k8s_namespace = "circleguard-stage"
}

output "gke_cluster_name" {
  value = module.gke.cluster_name
}
