output "raw_bucket" {
  description = "Raw uploads bucket name"
  value       = aws_s3_bucket.raw.id
}

output "reports_bucket" {
  description = "Reports bucket name"
  value       = aws_s3_bucket.reports.id
}

output "kms_key_arn" {
  description = "ARN of the KMS key used for the buckets"
  value       = aws_kms_key.s3.arn
}
