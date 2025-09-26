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
