module "vpc" {
  source        = "../../modules/vpc"
  project_id    = var.project_id
  region        = var.region
  name          = "circleguard-prod"
  subnet_cidr   = "10.30.0.0/24"
  pods_cidr     = "10.30.4.0/22"
  services_cidr = "10.30.8.0/24"
}

module "gke" {
  source              = "../../modules/gke"
  project_id          = var.project_id
  region              = var.region
  cluster_name        = "circleguard-prod"
  network_id          = module.vpc.network_id
  subnet_id           = module.vpc.subnet_id
  pods_range_name     = module.vpc.pods_range_name
  services_range_name = module.vpc.services_range_name
  node_count          = 1
  min_node_count      = 0
  max_node_count      = 5
  machine_type        = "e2-standard-2"
  use_spot            = false
  disk_size_gb        = 50
}

module "secrets" {
  source      = "../../modules/secrets"
  project_id  = var.project_id
  environment = "prod"
  secrets = {
    "cg-db-password-prod"        = "PostgreSQL password for prod"
    "cg-jwt-secret-prod"         = "JWT signing secret for prod"
    "cg-dockerhub-user-prod"     = "Docker Hub username"
    "cg-dockerhub-password-prod" = "Docker Hub password or token"
    "cg-mail-password-prod"      = "SMTP password for notifications"
  }
}

module "iam" {
  source        = "../../modules/iam"
  project_id    = var.project_id
  environment   = "prod"
  k8s_namespace = "circleguard-production"
}

output "gke_cluster_name" {
  value = module.gke.cluster_name
}
