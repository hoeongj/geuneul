# ElastiCache Redis (P3 날씨 초단기실황 TTL 캐시, CLAUDE.md §7).
# 단일 노드 cache.t3.micro — 신규계정 프리티어(750h/월, 12개월) 대상 노드 타입. 프리티어 소진 시 ~$12/월.
# 사설 서브넷 + SG로 ECS 태스크에서만 접근(6379). TLS 미사용(사설망·SG 잠금) → 앱 REDIS_SSL=false.
#
# ⚠️ 비용 발생 리소스. terraform apply는 사용자 확인 후 실행한다.
# 캐시는 non-critical 계층(RedisCacheConfig의 CacheErrorHandler가 장애를 삼킴)이라 이게 없어도 앱은
# 기상청을 직접 호출하며 동작한다. ALB 헬스체크는 의도적으로 Redis에 결합하지 않는다(WORKLOG 참고).

resource "aws_elasticache_subnet_group" "redis" {
  name       = "${var.project}-redis"
  subnet_ids = aws_subnet.private[*].id
  tags       = { Name = "${var.project}-redis-subnet" }
}

resource "aws_security_group" "redis" {
  name        = "${var.project}-redis-sg"
  description = "ElastiCache Redis: inbound 6379 from ECS tasks only"
  vpc_id      = aws_vpc.main.id
  ingress {
    description     = "Redis from ECS tasks only"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "${var.project}-redis-sg" }
}

resource "aws_elasticache_cluster" "redis" {
  cluster_id           = "${var.project}-redis"
  engine               = "redis"
  node_type            = "cache.t3.micro" # 프리티어 대상
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  engine_version       = "7.1"
  port                 = 6379
  subnet_group_name    = aws_elasticache_subnet_group.redis.name
  security_group_ids   = [aws_security_group.redis.id]
  apply_immediately    = true
  tags                 = { Name = "${var.project}-redis" }
}
