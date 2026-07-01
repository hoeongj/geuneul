# RDS PostgreSQL (PostGIS는 Flyway V1의 CREATE EXTENSION으로 활성화).
# 프리티어: db.t3.micro, 20GB gp3, Single-AZ.

resource "aws_db_subnet_group" "db" {
  name       = "${var.project}-db"
  subnet_ids = aws_subnet.private[*].id
  tags       = { Name = "${var.project}-db-subnet" }
}

resource "aws_db_instance" "postgres" {
  identifier             = "${var.project}-db"
  engine                 = "postgres"
  engine_version         = "16"
  instance_class         = "db.t3.micro" # 프리티어 대상
  allocated_storage      = 20
  storage_type           = "gp3"
  db_name                = var.db_name
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.db.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false
  skip_final_snapshot    = true
  deletion_protection    = false
  apply_immediately      = true
  tags                   = { Name = "${var.project}-db" }
}
