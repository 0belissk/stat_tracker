variable "name_prefix" {
  description = "Prefix for naming the WAF resources"
  type        = string
}

variable "scope" {
  description = "Scope for the Web ACL (REGIONAL or CLOUDFRONT)"
  type        = string
  default     = "REGIONAL"
}

variable "max_body_size_bytes" {
  description = "Maximum allowed request body size in bytes before blocking"
  type        = number
  default     = 1048576
}

variable "metric_name" {
  description = "Metric name for the Web ACL"
  type        = string
  default     = null
}

variable "tags" {
  description = "Tags to apply to the WAF resources"
  type        = map(string)
  default     = {}
}
