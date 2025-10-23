output "state_machine_arn" {
  description = "ARN of the CSV processing Step Functions state machine"
  value       = aws_sfn_state_machine.csv_pipeline.arn
}

output "state_machine_name" {
  description = "Name of the CSV processing Step Functions state machine"
  value       = aws_sfn_state_machine.csv_pipeline.name
}

output "dlq_arn" {
  description = "ARN of the dead-letter SQS queue"
  value       = aws_sqs_queue.dlq.arn
}

output "dlq_url" {
  description = "URL of the dead-letter SQS queue"
  value       = aws_sqs_queue.dlq.id
}

output "role_arn" {
  description = "IAM role assumed by the state machine"
  value       = aws_iam_role.state_machine.arn
}

output "log_group_name" {
  description = "CloudWatch Logs group used for Step Functions execution logs"
  value       = aws_cloudwatch_log_group.state_machine.name
}
