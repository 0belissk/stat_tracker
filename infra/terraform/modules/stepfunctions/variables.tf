variable "name_prefix" {
  description = "Prefix used for Step Functions resources"
  type        = string
}

variable "region" {
  description = "AWS region for CloudWatch widgets"
  type        = string
}

variable "validate_lambda_arn" {
  description = "todo: (Lambda function ARN for the CSV validation step)"
  type        = string
}

variable "transform_lambda_arn" {
  description = "todo: (Lambda function ARN for the CSV transform step)"
  type        = string
}

variable "quality_check_lambda_arn" {
  description = "todo: (Lambda function ARN for the stats quality check step)"
  type        = string
}

variable "persist_lambda_arn" {
  description = "todo: (Lambda function ARN for the CSV persistence step)"
  type        = string
}

variable "event_bus_name" {
  description = "Name or ARN of the EventBridge bus used for csv.validated notifications"
  type        = string
  default     = "default"
}

variable "event_source" {
  description = "Event source used when emitting csv.validated events"
  type        = string
  default     = "stat.tracker.csv"
}

variable "event_detail_type" {
  description = "Detail type used when emitting csv.validated events"
  type        = string
  default     = "csv.validated"
}

variable "retry_interval_seconds" {
  description = "Base interval (seconds) before retrying failed tasks"
  type        = number
  default     = 2
}

variable "retry_max_attempts" {
  description = "Maximum number of retry attempts for failed tasks"
  type        = number
  default     = 3
}

variable "retry_backoff_rate" {
  description = "Exponential backoff multiplier between retries"
  type        = number
  default     = 2.0
}

variable "retry_jitter_strategy" {
  description = "Jitter strategy for retries (e.g., FULL or NONE)"
  type        = string
  default     = "FULL"
}

variable "log_retention_in_days" {
  description = "CloudWatch Logs retention period for Step Functions execution logs"
  type        = number
  default     = 30
}

variable "tags" {
  description = "Additional resource tags"
  type        = map(string)
  default     = {}
}

variable "custom_metrics_namespace" {
  description = "CloudWatch namespace for ingestion metrics"
  type        = string
}

variable "ingest_duration_metric_service" {
  description = "Dimension value identifying the ingestion pipeline for ingest_duration"
  type        = string
}

variable "ingest_duration_metric_stage" {
  description = "Stage/environment dimension for ingest_duration"
  type        = string
}

variable "ingest_duration_alarm_threshold_ms" {
  description = "Threshold in milliseconds for the ingest_duration p95 alarm"
  type        = number
  default     = 900000
}

variable "ingest_duration_alarm_evaluation_periods" {
  description = "Evaluation periods for the ingest_duration alarm"
  type        = number
  default     = 3
}

variable "ingest_duration_alarm_datapoints" {
  description = "Datapoints to alarm for the ingest_duration metric"
  type        = number
  default     = 2
}

variable "ingest_duration_alarm_actions" {
  description = "Optional alarm action ARNs for ingest_duration breaches"
  type        = list(string)
  default     = []
}
