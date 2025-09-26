variable "table_name" {
  description = "Name of the DynamoDB table"
  type        = string
}

variable "kms_key_arn" {
  description = "ARN of the KMS key used to encrypt the table"
  type        = string
}

variable "with_team_gsi" {
  description = "Whether to enable the optional team global secondary index"
  type        = bool
  default     = true
}
