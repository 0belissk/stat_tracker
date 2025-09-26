output "user_pool_id" {
  description = "ID of the Cognito user pool"
  value       = aws_cognito_user_pool.this.id
}

output "app_client_id" {
  description = "ID of the Cognito app client"
  value       = aws_cognito_user_pool_client.app.id
}
