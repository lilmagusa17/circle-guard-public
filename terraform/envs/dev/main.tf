module "vpc" {
  source        = "../../modules/vpc"
  project_id    = var.project_id
  region        = var.region
  name          = "circleguard-dev"
  subnet_cidr   = "10.10.0.0/24"
  pods_cidr     = "10.10.4.0/22"
  services_cidr = "10.10.8.0/24"
}

module "gke" {
  source              = "../../modules/gke"
  project_id          = var.project_id
  region              = var.region
  cluster_name        = "circleguard-dev"
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

# Artifact Registry is global — only created in dev env
module "artifact_registry" {
  source        = "../../modules/artifact_registry"
  project_id    = var.project_id
  location      = var.region
  repository_id = "circleguard"
}

module "secrets" {
  source      = "../../modules/secrets"
  project_id  = var.project_id
  environment = "dev"
  secrets = {
    "cg-db-password-dev"        = "PostgreSQL password for dev"
    "cg-jwt-secret-dev"         = "JWT signing secret for dev"
    "cg-dockerhub-user-dev"     = "Docker Hub username"
    "cg-dockerhub-password-dev" = "Docker Hub password or token"
    "cg-mail-password-dev"      = "SMTP password for notifications"
  }
}

module "iam" {
  source        = "../../modules/iam"
  project_id    = var.project_id
  environment   = "dev"
  k8s_namespace = "circleguard-dev"
}

output "gke_cluster_name" {
  value = module.gke.cluster_name
}

output "artifact_registry_url" {
  value = module.artifact_registry.repository_url
}

output "eso_sa_email" {
  value = module.iam.eso_sa_email
}
