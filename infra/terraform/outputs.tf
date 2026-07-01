output "alb_url" {
  description = "앱 공개 URL (health: /actuator/health, swagger: /swagger-ui.html)"
  value       = "http://${aws_lb.app.dns_name}"
}

output "ecr_repository_url" {
  description = "이미지 push 대상"
  value       = aws_ecr_repository.backend.repository_url
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service" {
  value = aws_ecs_service.app.name
}

output "rds_endpoint" {
  value = aws_db_instance.postgres.address
}

output "github_actions_role_arn" {
  description = "GitHub 레포 Secrets에 AWS_ROLE_ARN 으로 등록"
  value       = aws_iam_role.github_actions.arn
}
