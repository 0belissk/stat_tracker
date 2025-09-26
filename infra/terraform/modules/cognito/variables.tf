variable "name_prefix" {
  description = "Prefix used to name Cognito resources"
  type        = string
}

variable "callback_urls" {
  description = "Allowed OAuth callback URLs"
  type        = list(string)
  default     = ["https://example.com/callback"]
}

variable "logout_urls" {
  description = "Allowed OAuth logout URLs"
  type        = list(string)
  default     = ["https://example.com/logout"]
}
