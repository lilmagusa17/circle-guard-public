output "eso_sa_email" {
  value = google_service_account.eso.email
}

output "service_sa_emails" {
  value = { for k, v in google_service_account.services : k => v.email }
}
