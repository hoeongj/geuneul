# 아키텍처 의사결정 기록 (ADR)

주요 설계 결정을 "문제 → 결정 → 검토한 대안 → 결과 → 근거(2026 트렌드 확인)" 형식으로 남긴다.
각 문서는 그 결정이 왜 그렇게 내려졌는지를 코드/라이브 상태와 함께 설명한다.

| # | 결정 | 상태 |
|---|---|---|
| [0001](./0001-geometry-storage-geography-function-index.md) | 좌표는 `geometry(Point,4326)` 저장, 거리 연산은 `geography` 함수 인덱스(GiST) | 승인 |
| [0002](./0002-idempotent-ingestion-upsert.md) | 공공데이터 인제스천은 자연키 `ON CONFLICT` 배치 upsert(멱등) | 승인 |
| [0003](./0003-geocoding-pipeline.md) | 좌표 미제공 데이터는 geocode-before-insert + 저장 좌표 재사용 | 승인 |
| [0004](./0004-frontend-same-origin-proxy.md) | 프론트는 동일 오리진 서버 프록시(BFF)로만 백엔드 접근 | 승인 |
| [0005](./0005-cafe-features-as-summer-scenario.md) | 카공/카페 기능은 "여름 실내 시나리오"로 흡수(리뷰앱화 금지) | 부분 채택 |
| [0006](./0006-study-space-coverage-expansion.md) | 공부 가능 공간 데이터 커버리지 확장(survival 레이어) | 제안 |
| [0007](./0007-survival-score-sql-signals-java-compose.md) | `survival_score` — 시공간 신호는 SQL 뷰, 가중치 조립은 순수 함수 | 승인 |
| [0008](./0008-recommendations-scenario-weighted-ranking.md) | 추천(`/recommendations`) — survival_score에 시나리오 가중을 얹은 2단 랭킹 | 승인 |
| [0009](./0009-weather-comfort-additive-restore.md) | survival_score comfort — 기온(체감) 신호를 comfort_score에 additive 복원 | 승인 |
| [0010](./0010-ai-summary-openrouter-provider.md) | AI 한줄 요약 — 프로바이더는 OpenRouter(Anthropic 키 부재로 이탈), 상세 전용 additive | 승인 |
| [0011](./0011-scheduled-public-data-sync.md) | 공공데이터 주기 동기화 — EventBridge Scheduler(Universal Target) → ECS RunTask + Postgres advisory lock | 승인(스캐폴드, 미활성) |
| [0012](./0012-ecs-service-autoscaling.md) | ECS Service Auto Scaling — CPU target tracking, min=1/max=3, 기본 ENABLED | 승인(스캐폴드, apply 대기) |
