# DB 비밀번호를 SSM Parameter Store(SecureString, 무료)에 저장 → ECS task def에서 secrets로 주입.
# 값은 var.db_password(터미널/tfvars에서만). 레포엔 안 들어감.
resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project}/db_password"
  type  = "SecureString"
  value = var.db_password
}

# BFF↔백엔드 공유 시크릿 (익명 제보 레이트리밋 XFF 신뢰경계, TS-008). 값은 var.proxy_secret(tfvars)로만.
# 실행 롤 SSM 정책이 /${var.project}/* 와일드카드라 별도 IAM 변경 불필요.
resource "aws_ssm_parameter" "proxy_secret" {
  name  = "/${var.project}/proxy_secret"
  type  = "SecureString"
  value = var.proxy_secret
}

# 기상청 serviceKey (P3 날씨 캐시). 값은 var.kma_service_key(tfvars)로만. 실행 롤 SSM 정책은 와일드카드.
resource "aws_ssm_parameter" "kma_service_key" {
  name  = "/${var.project}/kma_service_key"
  type  = "SecureString"
  value = var.kma_service_key
}

# 소셜 로그인(P2) 자격증명 + JWT 서명키. 전부 /${var.project}/* 와일드카드 SSM 정책 → IAM 무변경.
resource "aws_ssm_parameter" "kakao_rest_api_key" {
  name  = "/${var.project}/kakao_rest_api_key"
  type  = "SecureString"
  value = var.kakao_rest_api_key
}

resource "aws_ssm_parameter" "kakao_client_secret" {
  name  = "/${var.project}/kakao_client_secret"
  type  = "SecureString"
  value = var.kakao_client_secret
}

resource "aws_ssm_parameter" "google_client_id" {
  name  = "/${var.project}/google_client_id"
  type  = "SecureString"
  value = var.google_client_id
}

resource "aws_ssm_parameter" "google_client_secret" {
  name  = "/${var.project}/google_client_secret"
  type  = "SecureString"
  value = var.google_client_secret
}

resource "aws_ssm_parameter" "jwt_secret" {
  name  = "/${var.project}/jwt_secret"
  type  = "SecureString"
  value = var.jwt_secret
}
