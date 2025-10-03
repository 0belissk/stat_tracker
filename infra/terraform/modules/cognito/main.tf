resource "aws_cognito_user_pool" "this" {
  name = "${var.name_prefix}-user-pool"
  username_attributes = ["email"]
  auto_verified_attributes = ["email"]
  password_policy { minimum_length = 8 }
}

resource "aws_cognito_user_pool_client" "app" {
  name         = "${var.name_prefix}-app-client"
  user_pool_id = aws_cognito_user_pool.this.id
  allowed_oauth_flows       = ["code"]
  allowed_oauth_scopes      = ["email", "openid", "profile"]
  allowed_oauth_flows_user_pool_client = true
  generate_secret           = false
  callback_urls             = var.callback_urls
  logout_urls               = var.logout_urls
  supported_identity_providers = ["COGNITO"]
  explicit_auth_flows = ["ALLOW_USER_PASSWORD_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"]
}

output "user_pool_id"     { value = aws_cognito_user_pool.this.id }
output "user_pool_arn"    { value = aws_cognito_user_pool.this.arn }
output "app_client_id"    { value = aws_cognito_user_pool_client.app.id }
