output "cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.this.name
}

output "service_name" {
  description = "ECS service name"
  value       = aws_ecs_service.this.name
}

output "service_security_group_id" {
  description = "Security group ID applied to ECS tasks"
  value       = aws_security_group.service.id
}

output "alb_dns_name" {
  description = "Application Load Balancer DNS name"
  value       = aws_lb.app.dns_name
}

output "alb_arn" {
  description = "ARN of the Application Load Balancer"
  value       = aws_lb.app.arn
}

output "target_group_arn" {
  description = "ARN of the target group fronting the service"
  value       = aws_lb_target_group.app.arn
}

output "log_group_name" {
  description = "CloudWatch Logs group storing JSON service logs"
  value       = aws_cloudwatch_log_group.app.name
}

output "dashboard_name" {
  description = "CloudWatch dashboard summarising latency and 5xx errors"
  value       = aws_cloudwatch_dashboard.app.dashboard_name
}

output "target_5xx_alarm_arn" {
  description = "CloudWatch alarm ARN watching for 5xx responses"
  value       = aws_cloudwatch_metric_alarm.target_5xx.arn
}
