variable "aws_region" {
  description = "AWS 리전 (서울)"
  type        = string
  default     = "ap-northeast-2"
}

variable "project" {
  type    = string
  default = "geuneul"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "db_name" {
  type    = string
  default = "geuneul"
}

variable "db_username" {
  type    = string
  default = "geuneul"
}

variable "db_password" {
  description = "RDS 마스터 비밀번호. terraform.tfvars(gitignore) 또는 TF_VAR_db_password 환경변수로 주입. 절대 커밋 금지."
  type        = string
  sensitive   = true
}

variable "container_image" {
  description = "ECS 태스크 이미지. 비우면 ECR:latest. 실제 배포 태그는 GitHub Actions(deploy.yml)가 갱신하며, TF는 ignore_changes로 되돌리지 않음."
  type        = string
  default     = ""
}

variable "github_repo" {
  description = "OIDC 신뢰 대상 (owner/repo)"
  type        = string
  default     = "ghdtjdwn/geuneul"
}

variable "task_cpu" {
  description = "Fargate CPU (256 = 0.25 vCPU, 비용 최소)"
  type        = string
  default     = "256"
}

variable "task_memory" {
  description = "Fargate 메모리(MB). Spring Boot+Hibernate+Flyway엔 512는 빠듯 → 1024 권장(cpu 256과 유효 조합)."
  type        = string
  default     = "1024"
}
