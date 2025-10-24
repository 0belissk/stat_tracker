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
  description = "todo: (verified SES sender email address for notify-report-ready emails)"
  default     = "todo: (verified SES sender email address)"
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
  description = "todo: (S3 bucket name that stores the notify-report-ready deployment package)"
  default     = "todo: (S3 bucket name for notify-report-ready package)"
}

variable "notify_report_ready_package_key" {
  description = "todo: (S3 object key for the notify-report-ready deployment artifact)"
  default     = "todo: (S3 object key for notify-report-ready package)"
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

variable "players_api_image_uri" {
  description = "todo: (ECR image URI with tag for the players-api service, e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/players-api:main)"
  default     = "todo: (ECR image URI with tag for the players-api service)"
}

variable "players_api_desired_count" {
  default = 1
}

variable "players_api_certificate_arn" {
  description = "todo: (ACM certificate ARN for the public HTTPS listener)"
  default     = null
}

variable "players_api_allowed_ingress_cidrs" {
  description = "CIDRs allowed to access the ALB"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "players_api_alarm_actions" {
  description = "todo: (ARNs for alarm notifications, e.g., SNS topic)"
  type        = list(string)
  default     = []
}

variable "csv_validate_lambda_arn" {
  description = "todo: (Lambda ARN for the csv-validate function used by the Step Functions pipeline)"
  default     = "todo: (Lambda ARN for csv-validate)"
}

variable "csv_transform_lambda_arn" {
  description = "todo: (Lambda ARN for the transform step invoked by the Step Functions pipeline)"
  default     = "todo: (Lambda ARN for transform step)"
}

variable "csv_quality_check_lambda_arn" {
  description = "todo: (Lambda ARN for the stats quality check step invoked by the Step Functions pipeline)"
  default     = "todo: (Lambda ARN for stats quality check)"
}

variable "csv_persist_lambda_arn" {
  description = "todo: (Lambda ARN for the persist step invoked by the Step Functions pipeline)"
  default     = "todo: (Lambda ARN for persist step)"
}

variable "csv_pipeline_event_source" {
  default = "stat.tracker.csv"
}

variable "csv_validated_event_detail_type" {
  default = "csv.validated"
}
