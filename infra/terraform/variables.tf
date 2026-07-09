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

variable "proxy_secret" {
  description = "BFF(Vercel)↔백엔드 공유 시크릿. 익명 제보 레이트리밋이 BFF가 준 실 클라이언트 IP를 신뢰해 XFF 위조 우회를 차단(ProxyClientResolver, TS-008). terraform.tfvars(gitignore) 또는 TF_VAR_proxy_secret로 주입. 절대 커밋 금지. (SSM은 빈 값을 거부하므로 default 없이 필수로 둔다.)"
  type        = string
  sensitive   = true
}

variable "kma_service_key" {
  description = "기상청 초단기실황(getUltraSrtNcst) serviceKey. data.go.kr **디코딩 키**(이중 인코딩 함정 회피). P3 날씨 캐시가 사용. terraform.tfvars(gitignore) 또는 TF_VAR_kma_service_key로 주입. 절대 커밋 금지. (SSM은 빈 값을 거부하므로 default 없이 필수.)"
  type        = string
  sensitive   = true
}

variable "kakao_rest_api_key" {
  description = "카카오 앱 REST API 키. 지오코딩 + 카카오 로그인 client_id 겸용. terraform.tfvars(gitignore)로 주입, 커밋 금지."
  type        = string
  sensitive   = true
}

variable "kakao_client_secret" {
  description = "카카오 로그인 Client Secret(콘솔 [플랫폼 키]에서 활성화 ON). terraform.tfvars(gitignore)로 주입, 커밋 금지."
  type        = string
  sensitive   = true
}

variable "google_client_id" {
  description = "구글 OAuth 웹 클라이언트 ID. terraform.tfvars(gitignore)로 주입, 커밋 금지."
  type        = string
  sensitive   = true
}

variable "google_client_secret" {
  description = "구글 OAuth 클라이언트 시크릿. terraform.tfvars(gitignore)로 주입, 커밋 금지."
  type        = string
  sensitive   = true
}

variable "jwt_secret" {
  description = "JWT HS256 서명키(≥32바이트). terraform.tfvars(gitignore)로 주입, 커밋 금지."
  type        = string
  sensitive   = true
}

variable "openrouter_api_key" {
  description = "OpenRouter(OpenAI 호환) API 키 — P3 AI 한줄 요약(곁다리, ADR-0010). Anthropic 키가 없어 OpenRouter로 이탈(WORKLOG 기록). terraform.tfvars(gitignore) 또는 TF_VAR_openrouter_api_key로 주입. 절대 커밋 금지. (SSM은 빈 값을 거부하므로 default 없이 필수 — 값을 아직 못 넣으면 이 변수를 빈 문자열로 채운 tfvars 항목을 추가하지 말고 apply를 보류할 것.)"
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
