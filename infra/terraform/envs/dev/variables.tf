variable "region" { default = "us-east-1" }
variable "name_prefix" { default = "vsm-dev" }
variable "raw_bucket_name" { default = "vsm-raw-uploads-pq-dev" }
variable "report_bucket_name" { default = "vsm-report-texts-pq-dev" }
variable "ddb_table_name" { default = "vsm-main" }

# ⚠️ Change the two S3 bucket names to globally unique values (e.g., add your initials) here or create dev.auto.tfvars with overrides.

variable "notify_report_ready_config_path" {
  default = "/stat-tracker/notify-report-ready/"
}

variable "notify_report_ready_sender" {
  description = "TODO: replace with a verified SES sender email address"
  default     = "todo"
}

variable "notify_report_ready_ses_identity" {
  default = null
}

variable "notify_report_ready_email_subject" {
  default = "Report ready: {{reportName}}"
}

variable "notify_report_ready_email_template" {
  default = <<EOT
Hello {{recipientName}},

Your {{reportType}} report "{{reportName}}" is ready.
Download it here: {{downloadUrl}}

Thanks,
Stat Tracker Team
EOT
}

variable "notify_report_ready_link_expiry_seconds" {
  default = 3600
}

variable "notify_report_ready_package_bucket" {
  description = "TODO: S3 bucket where the notify-report-ready deployment package is uploaded"
  default     = "todo"
}

variable "notify_report_ready_package_key" {
  description = "TODO: S3 key for the notify-report-ready deployment artifact"
  default     = "todo"
}

variable "notify_report_ready_event_bus_name" {
  default = "default"
}

variable "notify_report_ready_event_source" {
  default = "stat.tracker.reports"
}

variable "notify_report_ready_event_detail_type" {
  default = "report.created"
}
