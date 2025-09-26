resource "aws_dynamodb_table" "this" {
  name         = var.table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"
  range_key    = "SK"

  attribute { name = "PK"; type = "S" }
  attribute { name = "SK"; type = "S" }
  attribute { name = "GSI1PK"; type = "S" }
  attribute { name = "GSI1SK"; type = "S" }

  global_secondary_index {
    name            = "GSI1"
    hash_key        = "GSI1PK"
    range_key       = "GSI1SK"
    projection_type = "ALL"
  }

  dynamic "global_secondary_index" {
    for_each = var.with_team_gsi ? [1] : []
    content {
      name            = "GSI2"
      hash_key        = "GSI2PK"
      range_key       = "GSI2SK"
      projection_type = "ALL"
    }
  }

  server_side_encryption {
    enabled     = true
    kms_key_arn = var.kms_key_arn
  }

  point_in_time_recovery { enabled = true }

  tags = { Name = var.table_name }
}
