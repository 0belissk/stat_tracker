resource "aws_dynamodb_table" "this" {
  name         = var.table_name
  billing_mode = "PAY_PER_REQUEST"

  hash_key  = "pk"
  range_key = "sk"

  attribute {
    name = "pk"
    type = "S"
  }

  attribute {
    name = "sk"
    type = "S"
  }

  attribute {
    name = "reportId"
    type = "S"
  }

  attribute {
    name = "team"
    type = "S"
  }

  global_secondary_index {
    name            = "reportId"
    hash_key        = "reportId"
    projection_type = "ALL"
  }

  dynamic "global_secondary_index" {
    for_each = var.with_team_gsi ? [1] : []

    content {
      name            = "team"
      hash_key        = "team"
      projection_type = "ALL"
    }
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled     = true
    kms_key_arn = var.kms_key_arn
  }

  tags = {
    Name = var.table_name
  }
}
