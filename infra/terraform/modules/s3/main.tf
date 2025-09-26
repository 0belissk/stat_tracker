// KMS Key for S3 SSE
resource "aws_kms_key" "s3" {
  description = "${var.name_prefix} S3 KMS key"
  deletion_window_in_days = 7
}

locals {
  buckets = {
    raw     = var.raw_bucket_name
    reports = var.report_bucket_name
  }
}

// Create two buckets: raw uploads + report texts
resource "aws_s3_bucket" "b" {
  for_each = local.buckets
  bucket   = each.value
}

resource "aws_s3_bucket_public_access_block" "b" {
  for_each = aws_s3_bucket.b
  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "sse" {
  for_each = aws_s3_bucket.b
  bucket   = each.value.id
  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.s3.arn
      sse_algorithm     = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_versioning" "v" {
  for_each = aws_s3_bucket.b
  bucket   = each.value.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_policy" "tls_only" {
  for_each = aws_s3_bucket.b
  bucket   = each.value.id
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Sid = "DenyInsecureTransport",
      Effect = "Deny",
      Principal = "*",
      Action = "s3:*",
      Resource = [
        each.value.arn,
        "${each.value.arn}/*"
      ],
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
}
