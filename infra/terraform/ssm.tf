# DB 비밀번호를 SSM Parameter Store(SecureString, 무료)에 저장 → ECS task def에서 secrets로 주입.
# 값은 var.db_password(터미널/tfvars에서만). 레포엔 안 들어감.
resource "aws_ssm_parameter" "db_password" {
  name  = "/${var.project}/db_password"
  type  = "SecureString"
  value = var.db_password
}
