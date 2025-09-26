resource "aws_ecr_repository" "this" {
  name = var.repo_name
  image_scanning_configuration { scan_on_push = true }
  encryption_configuration { encryption_type = "AES256" }
  force_delete = true
}

output "repository_url" { value = aws_ecr_repository.this.repository_url }
