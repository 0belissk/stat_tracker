terraform {
  required_version = ">= 1.6.0"
}

data "aws_event_bus" "target" {
  name_or_arn = var.event_bus_name
}

locals {
  retry_config = [
    {
      ErrorEquals     = ["States.ALL"],
      IntervalSeconds = var.retry_interval_seconds,
      MaxAttempts     = var.retry_max_attempts,
      BackoffRate     = var.retry_backoff_rate,
      JitterStrategy  = var.retry_jitter_strategy,
    }
  ]

  catch_config = [
    {
      ErrorEquals = ["States.ALL"],
      ResultPath  = "$.error",
      Next        = "SendToDlq",
    }
  ]

  state_machine_definition = {
    Comment = "CSV ingestion pipeline: validate → transform → quality-check → persist → notify"
    StartAt = "Validate"
    States = {
      Validate = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          "FunctionName" = var.validate_lambda_arn
          "Payload.$"    = "$"
        }
        ResultPath = "$.validate"
        Retry      = local.retry_config
        Catch      = local.catch_config
        Next       = "Transform"
      }

      Transform = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          "FunctionName" = var.transform_lambda_arn
          "Payload.$"    = "$.validate.Payload"
        }
        ResultPath = "$.transform"
        Retry      = local.retry_config
        Catch      = local.catch_config
        Next       = "QualityCheck"
      }

      QualityCheck = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          "FunctionName" = var.quality_check_lambda_arn
          "Payload.$"    = "$.transform.Payload"
        }
        ResultPath = "$.quality"
        Retry      = local.retry_config
        Catch      = local.catch_config
        Next       = "Persist"
      }

      Persist = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          "FunctionName" = var.persist_lambda_arn
          "Payload.$"    = "$.quality.Payload"
        }
        ResultPath = "$.persist"
        Retry      = local.retry_config
        Catch      = local.catch_config
        Next       = "Notify"
      }

      Notify = {
        Type     = "Task"
        Resource = "arn:aws:states:::events:putEvents"
        Parameters = {
          Entries = [
            {
              DetailType   = var.event_detail_type
              Source       = var.event_source
              EventBusName = data.aws_event_bus.target.name
              "Detail.$"   = "States.JsonToString($.persist.Payload)"
            }
          ]
        }
        Retry = local.retry_config
        Catch = local.catch_config
        End   = true
      }

      SendToDlq = {
        Type     = "Task"
        Resource = "arn:aws:states:::sqs:sendMessage"
        Parameters = {
          QueueUrl        = aws_sqs_queue.dlq.id
          "MessageBody.$" = "States.JsonToString($)"
        }
        End = true
      }
    }
  }
}

resource "aws_sqs_queue" "dlq" {
  name                      = "${var.name_prefix}-csv-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
  tags                      = merge(var.tags, { Purpose = "csv-dlq" })
}

data "aws_iam_policy_document" "state_machine_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["states.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "state_machine" {
  name               = "${var.name_prefix}-csv-pipeline"
  assume_role_policy = data.aws_iam_policy_document.state_machine_assume.json

  tags = merge(var.tags, { Name = "${var.name_prefix}-csv-pipeline" })
}

data "aws_iam_policy_document" "state_machine" {
  statement {
    sid     = "InvokePipelineLambdas"
    effect  = "Allow"
    actions = ["lambda:InvokeFunction"]
    resources = [
      var.validate_lambda_arn,
      var.transform_lambda_arn,
      var.quality_check_lambda_arn,
      var.persist_lambda_arn,
    ]
  }

  statement {
    sid       = "PublishValidatedEvent"
    effect    = "Allow"
    actions   = ["events:PutEvents"]
    resources = [data.aws_event_bus.target.arn]
  }

  statement {
    sid       = "SendToDlq"
    effect    = "Allow"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.dlq.arn]
  }
}

resource "aws_iam_role_policy" "state_machine" {
  role   = aws_iam_role.state_machine.id
  policy = data.aws_iam_policy_document.state_machine.json
}

resource "aws_cloudwatch_log_group" "state_machine" {
  name              = "/aws/vendedlogs/states/${var.name_prefix}-csv-pipeline"
  retention_in_days = var.log_retention_in_days
  tags              = var.tags
}

resource "aws_sfn_state_machine" "csv_pipeline" {
  name     = "${var.name_prefix}-csv-pipeline"
  role_arn = aws_iam_role.state_machine.arn

  definition = jsonencode(local.state_machine_definition)

  logging_configuration {
    include_execution_data = true
    level                  = "ERROR"
    log_destination        = aws_cloudwatch_log_group.state_machine.arn
  }

  tracing_configuration {
    enabled = true
  }

  tags = merge(var.tags, { Service = "stat-tracker" })
}
