variable "name_prefix" {
  description = "Prefix applied to resources for the notify-report-ready lambda"
  type        = string
}

variable "region" {
  description = "AWS region"
  type        = string
}

variable "reports_bucket_arn" {
  description = "ARN of the reports S3 bucket"
  type        = string
}

variable "config_secret_name" {
  description = "Secrets Manager secret name storing configuration JSON"
  type        = string
}

variable "sender_email" {
  description = "SES verified sender email address"
  type        = string
}

variable "ses_identity" {
  description = "Optional SES identity to scope permissions (defaults to sender_email)"
  type        = string
  default     = null
}

variable "email_subject" {
  description = "Plain text subject template"
  type        = string
}

variable "email_template" {
  description = "Plain text email body template"
  type        = string
}

variable "link_expiry_seconds" {
  description = "Signed URL expiry time in seconds"
  type        = number
  default     = 3600
}

variable "lambda_package_bucket" {
  description = "S3 bucket containing the lambda deployment artifact"
  type        = string
}

variable "lambda_package_key" {
  description = "S3 key of the lambda deployment artifact"
  type        = string
}

variable "lambda_description" {
  description = "Description for the lambda function"
  type        = string
  default     = "Sends report ready notifications"
}

variable "lambda_timeout" {
  description = "Lambda timeout in seconds"
  type        = number
  default     = 30
}

variable "event_bus_name" {
  description = "EventBridge bus name"
  type        = string
  default     = "default"
}

variable "event_source" {
  description = "Source value for report created events"
  type        = string
  default     = "stat.tracker.reports"
}

variable "event_detail_type" {
  description = "Detail type for report created events"
  type        = string
  default     = "report.created"
}
