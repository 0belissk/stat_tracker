# Dev Environment

1) `terraform init`  
2) `terraform plan`  
3) `terraform apply`

> Buckets must be globally unique. Set unique names in `variables.tf` or create `dev.auto.tfvars`.

> Provide real values for the ECS module inputs (`players_api_image_uri`, `players_api_certificate_arn`, alarm targets, etc.) before applying. Placeholders marked with `todo:` must be replaced.
