terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
}

provider "aws" { region = var.region }

locals {
  common_tags = {
    Environment = var.name_prefix
    ManagedBy   = "terraform"
  }
}

module "vpc" {
  source               = "../../modules/vpc"
  name                 = var.name_prefix
  cidr_block           = "10.0.0.0/16"
  azs                  = ["us-east-1a", "us-east-1b"]
  public_subnet_cidrs  = ["10.0.1.0/24", "10.0.2.0/24"]
  private_subnet_cidrs = ["10.0.11.0/24", "10.0.12.0/24"]
}

module "s3" {
  source             = "../../modules/s3"
  name_prefix        = var.name_prefix
  raw_bucket_name    = var.raw_bucket_name
  report_bucket_name = var.report_bucket_name
}

module "ddb" {
  source        = "../../modules/dynamodb"
  table_name    = var.ddb_table_name
  kms_key_arn   = module.s3.kms_key_arn
  with_team_gsi = true
}

module "cognito" {
  source      = "../../modules/cognito"
  name_prefix = var.name_prefix
}

module "ecr" {
  source    = "../../modules/ecr"
  repo_name = "players-api"
}

module "iam" {
  source                = "../../modules/iam"
  name_prefix           = var.name_prefix
  ddb_table_arn         = module.ddb.table_arn
  s3_reports_bucket_arn = "arn:aws:s3:::${var.report_bucket_name}"
  s3_raw_bucket_arn     = "arn:aws:s3:::${var.raw_bucket_name}"
  kms_key_arn           = module.s3.kms_key_arn
}

module "ecs_service" {
  source = "../../modules/ecs"

  name_prefix        = var.name_prefix
  region             = var.region
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  public_subnet_ids  = module.vpc.public_subnet_ids

  container_image   = var.players_api_image_uri
  container_port    = 8080
  health_check_path = "/actuator/health"
  desired_count     = var.players_api_desired_count
  task_cpu          = 512
  task_memory       = 1024

  execution_role_arn = module.iam.ecs_task_execution_role_arn
  task_role_arn      = module.iam.ecs_task_role_arn

  listener_certificate_arn = var.players_api_certificate_arn
  allowed_ingress_cidrs    = var.players_api_allowed_ingress_cidrs
  target_5xx_alarm_actions = var.players_api_alarm_actions

  tags = merge(local.common_tags, { Service = "players-api" })

  app_environment = {
    AWS_REGION                       = var.region
    REPORTS_TABLE_NAME               = module.ddb.table_name
    REPORTS_BUCKET_NAME              = module.s3.reports_bucket
    REPORTS_KEY_PREFIX               = "todo: (optional S3 prefix for report objects)"
    REPORTS_KMS_KEY_ARN              = module.s3.kms_key_arn
    EVENTBUS_NAME                    = var.notify_report_ready_event_bus_name
    EVENT_SOURCE                     = var.notify_report_ready_event_source
    EVENT_DETAIL_TYPE_REPORT_CREATED = var.notify_report_ready_event_detail_type
  }
}

module "notify_report_ready" {
  source                = "../../modules/notify_report_ready"
  name_prefix           = var.name_prefix
  region                = var.region
  reports_bucket_arn    = "arn:aws:s3:::${var.report_bucket_name}"
  config_path_prefix    = var.notify_report_ready_config_path
  sender_email          = var.notify_report_ready_sender
  ses_identity          = var.notify_report_ready_ses_identity
  email_subject         = var.notify_report_ready_email_subject
  email_template        = var.notify_report_ready_email_template
  link_expiry_seconds   = var.notify_report_ready_link_expiry_seconds
  lambda_package_bucket = var.notify_report_ready_package_bucket
  lambda_package_key    = var.notify_report_ready_package_key
  event_bus_name        = var.notify_report_ready_event_bus_name
  event_source          = var.notify_report_ready_event_source
  event_detail_type     = var.notify_report_ready_event_detail_type
}

module "csv_pipeline" {
  source = "../../modules/stepfunctions"

  name_prefix          = var.name_prefix
  validate_lambda_arn  = var.csv_validate_lambda_arn
  transform_lambda_arn = var.csv_transform_lambda_arn
  persist_lambda_arn   = var.csv_persist_lambda_arn
  event_bus_name       = var.notify_report_ready_event_bus_name
  event_source         = var.csv_pipeline_event_source
  event_detail_type    = var.csv_validated_event_detail_type
  tags                 = local.common_tags
}

output "vpc_id" { value = module.vpc.vpc_id }
output "public_subnets" { value = module.vpc.public_subnet_ids }
output "private_subnets" { value = module.vpc.private_subnet_ids }
output "s3_raw_bucket" { value = module.s3.raw_bucket }
output "s3_reports_bucket" { value = module.s3.reports_bucket }
output "ddb_table_name" { value = module.ddb.table_name }
output "cognito_user_pool_id" { value = module.cognito.user_pool_id }
output "cognito_app_client_id" { value = module.cognito.app_client_id }
output "ecr_repo" { value = module.ecr.repository_name }
output "ecs_task_role" { value = module.iam.ecs_task_role_name }
output "lambda_role" { value = module.iam.lambda_role_name }
output "players_api_alb_dns" { value = module.ecs_service.alb_dns_name }
output "players_api_dashboard" { value = module.ecs_service.dashboard_name }
output "players_api_5xx_alarm_arn" { value = module.ecs_service.target_5xx_alarm_arn }
output "csv_pipeline_state_machine_arn" { value = module.csv_pipeline.state_machine_arn }
output "csv_pipeline_dlq_url" { value = module.csv_pipeline.dlq_url }
