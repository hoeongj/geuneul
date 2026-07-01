resource "aws_ecs_cluster" "main" {
  name = var.project
  setting {
    name  = "containerInsights"
    value = "disabled" # 비용 절감. 관측성 심화(P4)에서 enabled.
  }
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.project}"
  retention_in_days = 14
}

resource "aws_ecs_task_definition" "app" {
  family                   = var.project
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([{
    name         = var.project
    image        = var.container_image != "" ? var.container_image : "${aws_ecr_repository.backend.repository_url}:latest"
    essential    = true
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment = [
      { name = "DB_HOST", value = aws_db_instance.postgres.address },
      { name = "DB_PORT", value = "5432" },
      { name = "DB_NAME", value = var.db_name },
      { name = "DB_USERNAME", value = var.db_username },
      { name = "PORT", value = "8080" }
    ]
    secrets = [
      { name = "DB_PASSWORD", valueFrom = aws_ssm_parameter.db_password.arn }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.app.name
        "awslogs-region"        = var.aws_region
        "awslogs-stream-prefix" = "app"
      }
    }
  }])

  # 이미지 태그는 CI(deploy.yml)가 갱신 → TF가 되돌리지 않도록.
  lifecycle {
    ignore_changes = [container_definitions]
  }
}

resource "aws_ecs_service" "app" {
  name            = var.project
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true # NAT 없이 ECR/인터넷 접근
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = var.project
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.http]

  # 배포는 CI가 task_definition 리비전을 갱신 → TF가 되돌리지 않도록.
  lifecycle {
    ignore_changes = [task_definition, desired_count]
  }
}
