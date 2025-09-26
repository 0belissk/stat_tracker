output "kms_key_arn" { value = aws_kms_key.s3.arn }
output "raw_bucket" { value = aws_s3_bucket.b["raw"].id }
output "reports_bucket" { value = aws_s3_bucket.b["reports"].id }
