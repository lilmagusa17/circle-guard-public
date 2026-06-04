terraform {
  backend "gcs" {
    bucket = "circle-guard-tfstate-final"
    prefix = "envs/prod"
  }
}
