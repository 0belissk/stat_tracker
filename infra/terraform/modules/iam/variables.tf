variable "name_prefix" {
  description = "Prefix used for naming IAM roles"
  type        = string
}

variable "ddb_table_arn" {
  description = "ARN of the DynamoDB table the workloads access"
  type        = string
}

variable "s3_reports_bucket_arn" {
  description = "ARN of the reports S3 bucket"
  type        = string
}

variable "s3_raw_bucket_arn" {
  description = "ARN of the raw uploads S3 bucket"
  type        = string
}

variable "kms_key_arn" {
  description = "ARN of the KMS CMK backing S3 SSE-KMS"
  type        = string
}

variable "region" {
  description = "AWS region where resources reside"
  type        = string
}

variable "event_bus_name" {
  description = "EventBridge event bus name the workloads can publish to"
  type        = string
}

variable "custom_metrics_namespace" {
  description = "CloudWatch namespace used for custom application metrics"
  type        = string
}
