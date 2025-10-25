variable "name_prefix" {
  description = "Prefix used for ECS resources (e.g., vsm-dev)"
  type        = string
}

variable "region" {
  description = "AWS region where the resources will be created"
  type        = string
}

variable "vpc_id" {
  description = "ID of the VPC hosting the ECS service"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs for ECS tasks"
  type        = list(string)
}

variable "public_subnet_ids" {
  description = "Public subnet IDs for the Application Load Balancer"
  type        = list(string)
}

variable "container_image" {
  description = "Full image URI (including tag) for the players-api task"
  type        = string
}

variable "container_port" {
  description = "Port exposed by the players-api container"
  type        = number
  default     = 8080
}

variable "desired_count" {
  description = "Number of Fargate tasks to run"
  type        = number
  default     = 1
}

variable "task_cpu" {
  description = "CPU units for the Fargate task (e.g., 512 = 0.5 vCPU)"
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "Memory (MiB) for the Fargate task"
  type        = number
  default     = 1024
}

variable "execution_role_arn" {
  description = "IAM role ARN for ECS task execution"
  type        = string
}

variable "task_role_arn" {
  description = "IAM role ARN assumed by the application container"
  type        = string
}

variable "log_retention_in_days" {
  description = "CloudWatch Logs retention in days for the service log group"
  type        = number
  default     = 30
}

variable "health_check_path" {
  description = "HTTP path used by the ALB for target health checks"
  type        = string
  default     = "/health"
}

variable "listener_certificate_arn" {
  description = "Optional ACM certificate ARN for enabling HTTPS"
  type        = string
  default     = null
}

variable "allowed_ingress_cidrs" {
  description = "List of CIDR blocks allowed to reach the ALB"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "xray_daemon_image" {
  description = "Container image for the AWS X-Ray daemon"
  type        = string
  default     = "public.ecr.aws/xray/aws-xray-daemon:3.3.11"
}

variable "target_5xx_alarm_threshold" {
  description = "Threshold for the ALB target 5xx alarm"
  type        = number
  default     = 1
}

variable "target_5xx_alarm_datapoints" {
  description = "Number of datapoints breaching before triggering the alarm"
  type        = number
  default     = 1
}

variable "target_5xx_alarm_evaluation_periods" {
  description = "Evaluation periods for the ALB target 5xx alarm"
  type        = number
  default     = 1
}

variable "target_5xx_alarm_actions" {
  description = "Optional list of ARNs (e.g., SNS topic) to notify on alarm"
  type        = list(string)
  default     = []
}

variable "custom_metrics_namespace" {
  description = "CloudWatch namespace for custom application metrics"
  type        = string
}

variable "report_latency_metric_service" {
  description = "Dimension value identifying the service for report_create_latency"
  type        = string
}

variable "report_latency_metric_stage" {
  description = "Stage/environment dimension for report_create_latency"
  type        = string
}

variable "report_latency_alarm_threshold_ms" {
  description = "Threshold in milliseconds for the report_create_latency p95 alarm"
  type        = number
  default     = 6000
}

variable "report_latency_alarm_evaluation_periods" {
  description = "Evaluation periods for the report_create_latency alarm"
  type        = number
  default     = 3
}

variable "report_latency_alarm_datapoints" {
  description = "Number of datapoints breaching before triggering the report_create_latency alarm"
  type        = number
  default     = 2
}

variable "report_latency_alarm_actions" {
  description = "Optional list of ARNs (e.g., SNS topic) to notify for the report_create_latency alarm"
  type        = list(string)
  default     = []
}

variable "tags" {
  description = "Common tags to apply to resources"
  type        = map(string)
  default     = {}
}

variable "app_environment" {
  description = "Map of environment variables injected into the application container"
  type        = map(string)
  default     = {}
}

variable "web_acl_arn" {
  description = "Optional WAFv2 Web ACL ARN to associate with the ALB"
  type        = string
  default     = null
}
