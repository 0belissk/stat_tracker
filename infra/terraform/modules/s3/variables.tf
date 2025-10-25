variable "name_prefix" {
  description = "Prefix used for naming the S3 resources"
  type        = string
}

variable "raw_bucket_name" {
  description = "Name of the raw uploads bucket"
  type        = string
}

variable "report_bucket_name" {
  description = "Name of the reports bucket"
  type        = string
}

variable "raw_bucket_retention_days" {
  description = "Number of days to retain raw uploads before expiration"
  type        = number
  default     = 30
}
