resource "aws_kms_key" "s3" {
  description             = "KMS key for ${var.name_prefix} S3 buckets"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = {
    Name = "${var.name_prefix}-s3-kms"
  }
}

resource "aws_kms_alias" "s3" {
  name          = "alias/${var.name_prefix}-s3"
  target_key_id = aws_kms_key.s3.id
}

resource "aws_s3_bucket" "raw" {
  bucket = var.raw_bucket_name

  tags = {
    Name = "${var.name_prefix}-raw"
  }
}

resource "aws_s3_bucket" "reports" {
  bucket = var.report_bucket_name

  tags = {
    Name = "${var.name_prefix}-reports"
  }
}

resource "aws_s3_bucket_versioning" "raw" {
  bucket = aws_s3_bucket.raw.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_versioning" "reports" {
  bucket = aws_s3_bucket.reports.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "raw" {
  bucket = aws_s3_bucket.raw.id

  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.s3.arn
      sse_algorithm     = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "reports" {
  bucket = aws_s3_bucket.reports.id

  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.s3.arn
      sse_algorithm     = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "raw" {
  bucket                  = aws_s3_bucket.raw.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "reports" {
  bucket                  = aws_s3_bucket.reports.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "raw" {
  bucket = aws_s3_bucket.raw.id

  rule {
    id     = "raw-retention"
    status = "Enabled"

    expiration {
      days = var.raw_bucket_retention_days
    }

    noncurrent_version_expiration {
      noncurrent_days = var.raw_bucket_retention_days
    }
  }
}

data "aws_iam_policy_document" "tls_only_raw" {
  statement {
    sid     = "ForceTLS"
    effect  = "Deny"
    actions = ["s3:*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    resources = [
      aws_s3_bucket.raw.arn,
      "${aws_s3_bucket.raw.arn}/*"
    ]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

data "aws_iam_policy_document" "tls_only_reports" {
  statement {
    sid     = "ForceTLS"
    effect  = "Deny"
    actions = ["s3:*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    resources = [
      aws_s3_bucket.reports.arn,
      "${aws_s3_bucket.reports.arn}/*"
    ]

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "raw" {
  bucket = aws_s3_bucket.raw.id
  policy = data.aws_iam_policy_document.tls_only_raw.json
}

resource "aws_s3_bucket_policy" "reports" {
  bucket = aws_s3_bucket.reports.id
  policy = data.aws_iam_policy_document.tls_only_reports.json
}
