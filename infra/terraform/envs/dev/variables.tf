variable "region" {
  default = "us-east-1"
}

variable "name_prefix" {
  default = "vsm-dev"
}

variable "raw_bucket_name" {
  default = "vsm-raw-uploads-pq-dev"
}

variable "report_bucket_name" {
  default = "vsm-report-texts-pq-dev"
}

variable "ddb_table_name" {
  default = "vsm-main"
}
