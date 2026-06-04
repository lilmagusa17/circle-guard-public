locals {
  services = [
    "auth",
    "dashboard",
    "file",
    "form",
    "gateway",
    "identity",
    "notification",
    "promotion",
  ]
}

# ESO service account — accesses Secret Manager on behalf of k8s workloads
resource "google_service_account" "eso" {
  project      = var.project_id
  account_id   = "eso-sa-${var.environment}"
  display_name = "External Secrets Operator (${var.environment})"
}

resource "google_project_iam_member" "eso_secret_accessor" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.eso.email}"
}

# Workload Identity binding — allows the KSA external-secrets/external-secrets
# to impersonate the ESO GSA
resource "google_service_account_iam_member" "eso_workload_identity" {
  service_account_id = google_service_account.eso.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[external-secrets/external-secrets]"
}

# Per-service GSAs for future Workload Identity use
resource "google_service_account" "services" {
  for_each     = toset(local.services)
  project      = var.project_id
  account_id   = "cg-${each.key}-${var.environment}"
  display_name = "CircleGuard ${each.key}-service (${var.environment})"
}

# Workload Identity binding per service
resource "google_service_account_iam_member" "service_workload_identity" {
  for_each           = toset(local.services)
  service_account_id = google_service_account.services[each.key].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[${var.k8s_namespace}/cg-${each.key}]"
}
