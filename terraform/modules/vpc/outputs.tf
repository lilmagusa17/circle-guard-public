output "network_id" {
  value = google_compute_network.vpc.id
}

output "subnet_id" {
  value = google_compute_subnetwork.subnet.id
}

output "pods_range_name" {
  value = "${var.name}-pods"
}

output "services_range_name" {
  value = "${var.name}-services"
}
