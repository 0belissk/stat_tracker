variable "name_prefix" { type = string }

variable "callback_urls" {
  type    = list(string)
  default = ["http://localhost:4200/auth/callback"]
}

variable "logout_urls" {
  type    = list(string)
  default = ["http://localhost:4200/"]
}
