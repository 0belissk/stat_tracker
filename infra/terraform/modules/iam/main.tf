// ECS task execution role
resource "aws_iam_role" "ecs_task_execution" {
  name = "${var.name_prefix}-ecs-task-exec"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_exec_policy" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

// ECS task role (app permissions — least-privilege placeholders)
resource "aws_iam_role" "ecs_task" {
  name = "${var.name_prefix}-ecs-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{ Effect = "Allow", Principal = { Service = "ecs-tasks.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_policy" "ecs_task_app" {
  name = "${var.name_prefix}-ecs-task-app"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      { Effect = "Allow", Action = ["dynamodb:PutItem","dynamodb:Query","dynamodb:GetItem","dynamodb:UpdateItem"], Resource = [var.ddb_table_arn, "${var.ddb_table_arn}/index/*"] },
      { Effect = "Allow", Action = ["s3:PutObject","s3:GetObject"], Resource = ["${var.s3_reports_bucket_arn}/*"] },
      { Effect = "Allow", Action = ["events:PutEvents"], Resource = "*" },
      { Effect = "Allow", Action = ["xray:PutTraceSegments","xray:PutTelemetryRecords"], Resource = "*" }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_app_attach" {
  role       = aws_iam_role.ecs_task.name
  policy_arn = aws_iam_policy.ecs_task_app.arn
}

// Lambda basic role (reuse for csv-validate/notify)
resource "aws_iam_role" "lambda_basic" {
  name = "${var.name_prefix}-lambda-basic"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{ Effect = "Allow", Principal = { Service = "lambda.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_policy" "lambda_policy" {
  name = "${var.name_prefix}-lambda-policy"
  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      { Effect = "Allow", Action = ["logs:CreateLogGroup","logs:CreateLogStream","logs:PutLogEvents"], Resource = "*" },
      { Effect = "Allow", Action = ["s3:GetObject","s3:PutObject"], Resource = ["${var.s3_raw_bucket_arn}/*","${var.s3_reports_bucket_arn}/*"] },
      { Effect = "Allow", Action = ["dynamodb:BatchWriteItem","dynamodb:PutItem","dynamodb:UpdateItem"], Resource = var.ddb_table_arn },
      { Effect = "Allow", Action = ["events:PutEvents","ses:SendEmail","ses:SendRawEmail"], Resource = "*" }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_policy_attach" {
  role       = aws_iam_role.lambda_basic.name
  policy_arn = aws_iam_policy.lambda_policy.arn
}

output "ecs_task_execution_role_arn" { value = aws_iam_role.ecs_task_execution.arn }
output "ecs_task_role_arn"           { value = aws_iam_role.ecs_task.arn }
output "lambda_role_arn"             { value = aws_iam_role.lambda_basic.arn }
