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
  storage_encrypted      = true # 저장 암호화(신규 인스턴스). ⚠️ 아래 lifecycle 참고.
  db_name                = var.db_name
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.db.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false
  # 자동 백업/PITR: 프리티어는 retention을 제약(7일 거부)한다. 유료 전환 시 backup_retention_period=7로
  # PITR을 켠다(백로그). 현재 데이터는 공공데이터+재현 가능 제보라 백업 부재 리스크가 낮다.
  skip_final_snapshot = true
  deletion_protection = false
  apply_immediately   = true
  tags                = { Name = "${var.project}-db" }

  # storage_encrypted는 기존 인스턴스에서 변경 불가(immutable) 속성이다. 라이브 DB는 이 설정 이전
  # 생성분이라 미암호화 상태이며, 여기에 넣으면 apply가 replace(=데이터 손실)를 시도한다.
  # → ignore_changes로 replace를 막는다(신규 배포만 암호화). 라이브 DB 암호화는 스냅샷→암호화 복원
  #   마이그레이션으로 별도 수행(백로그). 데이터는 공공데이터+재현 가능 제보라 재적재로 복구 가능.
  lifecycle {
    ignore_changes = [storage_encrypted]
  }
}
