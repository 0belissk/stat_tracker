terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
}

provider "aws" { region = var.region }

module "vpc" {
  source               = "../../modules/vpc"
  name                 = var.name_prefix
  cidr_block           = "10.0.0.0/16"
  azs                  = ["us-east-1a","us-east-1b"]
  public_subnet_cidrs  = ["10.0.1.0/24","10.0.2.0/24"]
  private_subnet_cidrs = ["10.0.11.0/24","10.0.12.0/24"]
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
  source                 = "../../modules/iam"
  name_prefix            = var.name_prefix
  ddb_table_arn          = module.ddb.table_arn
  s3_reports_bucket_arn  = "arn:aws:s3:::${var.report_bucket_name}"
  s3_raw_bucket_arn      = "arn:aws:s3:::${var.raw_bucket_name}"
}

output "vpc_id"                { value = module.vpc.vpc_id }
output "public_subnets"        { value = module.vpc.public_subnet_ids }
output "private_subnets"       { value = module.vpc.private_subnet_ids }
output "s3_raw_bucket"         { value = module.s3.raw_bucket }
output "s3_reports_bucket"     { value = module.s3.reports_bucket }
output "ddb_table_name"        { value = module.ddb.table_name }
output "cognito_user_pool_id"  { value = module.cognito.user_pool_id }
output "cognito_app_client_id" { value = module.cognito.app_client_id }
output "ecr_repo"              { value = module.ecr.repository_name }
output "ecs_task_role"         { value = module.iam.ecs_task_role_name }
output "lambda_role"           { value = module.iam.lambda_role_name }
