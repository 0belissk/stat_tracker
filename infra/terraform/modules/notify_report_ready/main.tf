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
  parameter_names = {
    sender_email       = "${var.config_path_prefix}sender-email"
    email_subject      = "${var.config_path_prefix}email-subject"
    email_template     = "${var.config_path_prefix}email-template"
    link_expiry_second = "${var.config_path_prefix}link-expiry-seconds"
  }

  reports_bucket_objects_arn = "${var.reports_bucket_arn}/*"
  ses_identity               = coalesce(var.ses_identity, var.sender_email)
  ses_identity_arn           = "arn:aws:ses:${var.region}:${data.aws_caller_identity.current.account_id}:identity/${local.ses_identity}"
}

resource "aws_ssm_parameter" "sender_email" {
  name        = local.parameter_names.sender_email
  description = "Sender email address for notify-report-ready"
  type        = "String"
  value       = var.sender_email
  overwrite   = true
}

resource "aws_ssm_parameter" "email_subject" {
  name        = local.parameter_names.email_subject
  description = "Email subject template for notify-report-ready"
  type        = "String"
  value       = var.email_subject
  overwrite   = true
}

resource "aws_ssm_parameter" "email_template" {
  name        = local.parameter_names.email_template
  description = "Email body template for notify-report-ready"
  type        = "String"
  value       = var.email_template
  overwrite   = true
}

resource "aws_ssm_parameter" "link_expiry_seconds" {
  name        = local.parameter_names.link_expiry_second
  description = "Download link expiry time for notify-report-ready"
  type        = "String"
  value       = tostring(var.link_expiry_seconds)
  overwrite   = true
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
    actions = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = [
      aws_ssm_parameter.sender_email.arn,
      aws_ssm_parameter.email_subject.arn,
      aws_ssm_parameter.email_template.arn,
      aws_ssm_parameter.link_expiry_seconds.arn,
    ]
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
      CONFIG_SSM_PARAMETER_PATH = var.config_path_prefix
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

output "config_parameter_prefix" {
  value = var.config_path_prefix
}
