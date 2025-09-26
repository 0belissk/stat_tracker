locals {
  raw_bucket_objects     = "${var.s3_raw_bucket_arn}/*"
  reports_bucket_objects = "${var.s3_reports_bucket_arn}/*"
  ddb_table_indexes_arn  = "${var.ddb_table_arn}/index/*"
}

data "aws_iam_policy_document" "ecs_assume" {
  statement {
    effect = "Allow"

    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_task_execution" {
  name               = "${var.name_prefix}-ecs-task-exec"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json

  tags = {
    Name = "${var.name_prefix}-ecs-task-exec"
  }
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "ecs_task" {
  statement {
    sid    = "DynamoDbAccess"
    effect = "Allow"

    actions = [
      "dynamodb:BatchGetItem",
      "dynamodb:BatchWriteItem",
      "dynamodb:DeleteItem",
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:Query",
      "dynamodb:UpdateItem",
      "dynamodb:DescribeTable"
    ]

    resources = [
      var.ddb_table_arn,
      local.ddb_table_indexes_arn
    ]
  }

  statement {
    sid    = "S3ObjectAccess"
    effect = "Allow"

    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject"
    ]

    resources = [
      local.raw_bucket_objects,
      local.reports_bucket_objects
    ]
  }

  statement {
    sid    = "S3List"
    effect = "Allow"

    actions = ["s3:ListBucket"]
    resources = [
      var.s3_raw_bucket_arn,
      var.s3_reports_bucket_arn
    ]
  }

  statement {
    sid    = "EventBridge"
    effect = "Allow"

    actions   = ["events:PutEvents"]
    resources = ["*"]
  }

  statement {
    sid    = "XRay"
    effect = "Allow"

    actions = [
      "xray:PutTraceSegments",
      "xray:PutTelemetryRecords"
    ]

    resources = ["*"]
  }
}

resource "aws_iam_role" "ecs_task" {
  name               = "${var.name_prefix}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json

  tags = {
    Name = "${var.name_prefix}-ecs-task"
  }
}

resource "aws_iam_role_policy" "ecs_task" {
  role   = aws_iam_role.ecs_task.id
  policy = data.aws_iam_policy_document.ecs_task.json
}

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    effect = "Allow"

    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda" {
  name               = "${var.name_prefix}-lambda"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json

  tags = {
    Name = "${var.name_prefix}-lambda"
  }
}

resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "lambda_extra" {
  statement {
    sid    = "S3Access"
    effect = "Allow"

    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:ListBucket"
    ]

    resources = [
      var.s3_raw_bucket_arn,
      local.raw_bucket_objects,
      var.s3_reports_bucket_arn,
      local.reports_bucket_objects
    ]
  }

  statement {
    sid    = "DynamoDbAccess"
    effect = "Allow"

    actions = [
      "dynamodb:BatchGetItem",
      "dynamodb:BatchWriteItem",
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:Query",
      "dynamodb:UpdateItem",
      "dynamodb:DescribeTable"
    ]

    resources = [
      var.ddb_table_arn,
      local.ddb_table_indexes_arn
    ]
  }

  statement {
    sid    = "EventBridge"
    effect = "Allow"

    actions   = ["events:PutEvents"]
    resources = ["*"]
  }

  statement {
    sid    = "SES"
    effect = "Allow"

    actions = [
      "ses:SendEmail",
      "ses:SendRawEmail"
    ]

    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "lambda_extra" {
  role   = aws_iam_role.lambda.id
  policy = data.aws_iam_policy_document.lambda_extra.json
}
