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

variable "datago_service_key" {
  description = "data.go.kr 계정 공통 serviceKey(디코딩 키, KMA_SERVICE_KEY와 같은 값). P3 공공데이터 오픈API 인제스천(전국도서관표준데이터 등)이 다운로드까지 무인화하는 데 쓴다(DataGoKrPublicLibraryClient). terraform.tfvars(gitignore) 또는 TF_VAR_datago_service_key로 주입. 절대 커밋 금지. (SSM은 빈 값을 거부하므로 default 없이 필수.)"
  type        = string
  sensitive   = true
}

variable "ai_summary_api_key" {
  description = "AI 요약 프로바이더(프로바이더 중립 OpenAI 호환 클라이언트, 현재 Mistral) API 키 — P3 AI 한줄 요약(곁다리, ADR-0010). Anthropic 키가 없어 이탈(WORKLOG 기록). terraform.tfvars(gitignore) 또는 TF_VAR_ai_summary_api_key로 주입. 절대 커밋 금지. (SSM은 빈 값을 거부하므로 default 없이 필수 — 값을 아직 못 넣으면 이 변수를 빈 문자열로 채운 tfvars 항목을 추가하지 말고 apply를 보류할 것.)"
  type        = string
  sensitive   = true
}

variable "ingest_schedule_enabled" {
  description = "EventBridge Scheduler(월1회 공공데이터 동기화 RunTask)를 실제로 켤지 여부. 기본 false(스캐줄 리소스는 생성되지만 DISABLED 상태 — apply해도 자동 실행되지 않는다). 사람이 검토 후 true로 바꿔 재적용해야 실행 시작(운영 안전장치, CLAUDE.md §0.2)."
  type        = bool
  default     = false
}

variable "container_image" {
  description = "ECS 태스크 이미지. 비우면 ECR:latest. 실제 배포 태그는 GitHub Actions(deploy.yml)가 갱신하며, TF는 ignore_changes로 되돌리지 않음."
  type        = string
  default     = ""
}

variable "github_repo" {
  description = "OIDC 신뢰 대상 (owner/repo)"
  type        = string
  default     = "hoeongj/geuneul"
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

variable "autoscaling_enabled" {
  description = "ECS Service Auto Scaling(CPU target tracking, P4)을 켤지 여부. 기본 true — ADR-0011의 공공데이터 스케줄과 달리 오토스케일링의 위험은 유계·가역적 비용뿐이라(autoscaling.tf 상단 주석·ADR-0012) 기본 활성으로 뒀다. 필요 시 false로 즉시 비활성화 가능(리소스 자체가 생성되지 않음 — desired_count는 ecs.tf의 수동값 1로 고정)."
  type        = bool
  default     = true
}

variable "autoscaling_max" {
  description = "ECS Service Auto Scaling 최대 태스크 수. min은 1로 고정(autoscaling.tf). 기본 3 — Fargate 0.25vCPU/1GB 태스크 기준 베이스라인(~$12/월) 대비 최대 3배(~$36/월)로 비용을 유계시킨 값($200 크레딧 보호, CLAUDE.md §7)."
  type        = number
  default     = 3
}
