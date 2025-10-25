terraform {
  required_version = ">= 1.6.0"
}

data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "assume_lambda" {
  statement {
    effect = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

locals {
  reports_bucket_objects_arn = "${var.reports_bucket_arn}/*"
  ses_identity               = coalesce(var.ses_identity, var.sender_email)
  ses_identity_arn           = "arn:aws:ses:${var.region}:${data.aws_caller_identity.current.account_id}:identity/${local.ses_identity}"
}

resource "aws_secretsmanager_secret" "config" {
  name        = var.config_secret_name
  description = "notify-report-ready configuration"

  tags = {
    Service = "stat-tracker"
  }
}

resource "aws_secretsmanager_secret_version" "config" {
  secret_id = aws_secretsmanager_secret.config.id
  secret_string = jsonencode({
    senderEmail       = var.sender_email
    emailSubject      = var.email_subject
    emailTemplate     = var.email_template
    linkExpirySeconds = var.link_expiry_seconds
  })
}

resource "aws_iam_role" "notify_report_ready" {
  name               = "${var.name_prefix}-notify-report-ready"
  assume_role_policy = data.aws_iam_policy_document.assume_lambda.json

  tags = {
    Name = "${var.name_prefix}-notify-report-ready"
  }
}

resource "aws_iam_role_policy_attachment" "basic_execution" {
  role       = aws_iam_role.notify_report_ready.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "notify_report_ready" {
  statement {
    sid    = "ReadConfig"
    effect = "Allow"
    actions = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
    resources = [aws_secretsmanager_secret.config.arn]
  }

  statement {
    sid       = "SendEmail"
    effect    = "Allow"
    actions   = ["ses:SendEmail"]
    resources = [local.ses_identity_arn]
  }

  statement {
    sid    = "SignReportDownloads"
    effect = "Allow"
    actions = ["s3:GetObject"]
    resources = [local.reports_bucket_objects_arn]
  }
}

resource "aws_iam_role_policy" "notify_report_ready" {
  role   = aws_iam_role.notify_report_ready.id
  policy = data.aws_iam_policy_document.notify_report_ready.json
}

resource "aws_lambda_function" "notify_report_ready" {
  function_name = "${var.name_prefix}-notify-report-ready"
  description   = var.lambda_description
  role          = aws_iam_role.notify_report_ready.arn
  handler       = "dist/handler.handler"
  runtime       = "nodejs18.x"
  s3_bucket     = var.lambda_package_bucket
  s3_key        = var.lambda_package_key
  timeout       = var.lambda_timeout

  environment {
    variables = {
      CONFIG_SECRET_ARN = aws_secretsmanager_secret.config.arn
    }
  }

  tags = {
    Service = "stat-tracker"
  }
}

resource "aws_cloudwatch_event_rule" "report_created" {
  name        = "${var.name_prefix}-report-created"
  description = "Triggers notify-report-ready when a report is created"
  event_bus_name = var.event_bus_name

  event_pattern = jsonencode({
    source      = [var.event_source]
    "detail-type" = [var.event_detail_type]
  })
}

resource "aws_cloudwatch_event_target" "notify_report_ready" {
  rule      = aws_cloudwatch_event_rule.report_created.name
  event_bus_name = aws_cloudwatch_event_rule.report_created.event_bus_name
  target_id = "notify-report-ready"
  arn       = aws_lambda_function.notify_report_ready.arn
}

resource "aws_lambda_permission" "allow_eventbridge" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.notify_report_ready.arn
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.report_created.arn
}

output "lambda_arn" {
  value = aws_lambda_function.notify_report_ready.arn
}

output "event_rule_name" {
  value = aws_cloudwatch_event_rule.report_created.name
}

output "config_secret_arn" {
  value = aws_secretsmanager_secret.config.arn
}
