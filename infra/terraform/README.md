# Terraform Infra — Day 10

This iteration extends the secure-by-default scaffolding with an operable ECS deployment:

- **VPC**: 2 public + 2 private subnets, IGW, 1 NAT, routes
- **S3**: raw + reports buckets, **KMS SSE**, **versioning**, **TLS-only policy**, **public access blocked**
- **DynamoDB**: PK/SK, **GSI1** (reportId), optional **GSI2** (team), **PITR**, **KMS SSE**
- **Cognito**: user pool + app client (code flow), email as username
- **ECR**: repository for players-api (scan on push)
- **IAM**: ECS task exec/task roles, Lambda role with least-privilege placeholders
- **ECS Fargate**: containerised `players-api` behind an Application Load Balancer, CloudWatch Logs (JSON), dashboard (p95 latency & 5xx), alarm on target 5xx, and AWS X-Ray sidecar

## Push the container image

1. Build the image in `services/players-api` (see that README for details).
2. Tag it with your repository URL + version, then push it to ECR.
3. Update the Terraform variable `players_api_image_uri` (for example in `dev.auto.tfvars`) with the pushed URI: `todo: (full ECR repository URI with tag)`.

## Terraform variables to review

- `players_api_certificate_arn` — set to `todo: (ACM certificate ARN)` to enable HTTPS + HTTP→HTTPS redirect.
- `players_api_allowed_ingress_cidrs` — tighten inbound CIDRs if needed.
- `players_api_alarm_actions` — provide ARNs such as `todo: (SNS topic ARN for 5xx alarm notifications)`.
- `players_api_desired_count` — scale out task count.
- `players_api` app environment overrides inside the module call — replace `REPORTS_KEY_PREFIX` with `todo: (S3 prefix for report objects)` or remove if unused.

## CloudWatch + X-Ray

- Logs land in `/ecs/<name_prefix>-players-api` with JSON payloads. Use MDC fields `correlationId` and `AWS-XRAY-TRACE-ID` to filter.
- Dashboard `${name_prefix}-players-api-observability` tracks p95 latency and 5xxs.
- Alarm `${name_prefix}-players-api-target-5xx` fires when the target group emits 5xxs (wire to SNS via the variable above).
- The task definition injects the AWS X-Ray daemon sidecar and defaults the sampling strategy to `default`. Override `XRAY_SAMPLING_STRATEGY` in Terraform or the console if you need custom sampling rules.

## Dev Usage
```bash
cd infra/terraform/envs/dev
terraform init
terraform plan
terraform apply
```
Adjust names in variables.tf (or create dev.auto.tfvars) if needed (S3 bucket names must be globally unique).
