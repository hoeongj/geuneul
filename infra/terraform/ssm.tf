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
