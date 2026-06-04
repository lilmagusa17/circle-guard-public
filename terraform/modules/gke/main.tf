resource "google_container_cluster" "cluster" {
  name     = var.cluster_name
  project  = var.project_id
  location = var.region

  network    = var.network_id
  subnetwork = var.subnet_id

  remove_default_node_pool = true
  initial_node_count       = 1
  deletion_protection      = false

  # Override default pool disk to avoid pd-balanced SSD quota issue
  node_config {
    disk_type    = "pd-standard"
    disk_size_gb = 30
    machine_type = "e2-medium"
  }

  ip_allocation_policy {
    cluster_secondary_range_name  = var.pods_range_name
    services_secondary_range_name = var.services_range_name
  }

  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  addons_config {
    http_load_balancing {
      disabled = false
    }
    horizontal_pod_autoscaling {
      disabled = false
    }
  }

  lifecycle {
    ignore_changes = [node_config, initial_node_count]
  }
}

resource "google_container_node_pool" "nodes" {
  name     = "default-pool"
  project  = var.project_id
  location = var.region
  cluster  = google_container_cluster.cluster.name

  initial_node_count = var.node_count

  autoscaling {
    min_node_count = var.min_node_count
    max_node_count = var.max_node_count
  }

  upgrade_settings {
    max_surge       = 0
    max_unavailable = 1
  }

  node_config {
    machine_type = var.machine_type
    disk_type    = "pd-standard"
    disk_size_gb = var.disk_size_gb

    spot = var.use_spot

    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform",
    ]

    workload_metadata_config {
      mode = "GKE_METADATA"
    }
  }

  management {
    auto_repair  = true
    auto_upgrade = true
  }
}
