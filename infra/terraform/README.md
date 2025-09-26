# Terraform Infra — Day 2

This completes Day 2 scaffolding with secure-by-default modules:

- **VPC**: 2 public + 2 private subnets, IGW, 1 NAT, routes
- **S3**: raw + reports buckets, **KMS SSE**, **versioning**, **TLS-only policy**, **public access blocked**
- **DynamoDB**: PK/SK, **GSI1** (reportId), optional **GSI2** (team), **PITR**, **KMS SSE**
- **Cognito**: user pool + app client (code flow), email as username
- **ECR**: repository for players-api (scan on push)
- **IAM**: ECS task exec/task roles, Lambda role with least-privilege placeholders

## Dev Usage
```bash
cd infra/terraform/envs/dev
terraform init
terraform plan
terraform apply
```
Adjust names in variables.tf (or create dev.auto.tfvars) if needed (S3 bucket names must be globally unique).
