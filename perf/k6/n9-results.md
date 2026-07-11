# N9 대규모 대비 — k6 부하 + task_cpu 512 before/after (2026-07-11, ADR-0025)

프로덕션(CloudFront→ALB→ECS Fargate + RDS PostGIS)에 **gentle** 부하(`PEAK_VUS=4`, 20s 램프 + 40s 피크 + 10s 하강,
read-only GET만 — CLAUDE.md "프로덕션 고부하 금지" 준수). `perf/k6/spatial_load.js`. 반경/kNN/bounds/추천 시나리오.

## before → after (task_cpu 256 → 512, memory 1024 유지)

| 지표(p95) | before (0.25 vCPU, rev66) | after (0.5 vCPU, rev67) | 변화 |
|---|---|---|---|
| geo_radius (반경, 스코어드 조인) | **2.68 s** | **1.39 s** | **−48%** |
| geo_nearest (kNN) | 238 ms | 98 ms | −59% |
| geo_bounds (뷰포트) | 395 ms | 210 ms | −47% |
| geo_reco (추천) | 681 ms | 670 ms | ≈ |
| 처리량(70s 창 요청수) | 616 | 1,296 | **×2.1** |
| http_req_failed | 0% | ~2% (롤아웃 꼬리 transient) | — |
| 앱 부팅(Started …) | ~93 s (TS-005, 0.25vCPU) | **70.09 s** | −25% |

## 해석
- 병목은 **CPU**였다: 0.25 vCPU에서 스코어드 반경 쿼리(place_report_signals·V13 LEFT JOIN)가 p95 2.68s로 튀었는데,
  vCPU를 2배로 올리자 **반경 p95 반토막 + 처리량 2배**. GiST 인덱스 경로는 그대로(인덱스가 아니라 CPU가 한계였음 — ADR-0012의
  "0.25vCPU라 포화가 빠르다" 가정을 부하로 실증). 부팅도 93s→70s로 단축(TS-005 근본 완화).
- **오토스케일링(min1/max3, CPU 60% target)은 유지** — task_cpu가 용량 레버였고, 60%는 AWS 권장 중앙값(ADR-0013).
  vCPU 2배로 태스크당 용량이 배가돼 같은 트래픽에서 스케일아웃이 늦춰짐(비용 유리). 임계를 흔들 이유 없음.
- **HikariCP 풀**은 태스크당 기본 10. max3 태스크 × 10 = 30 < RDS db.t3.micro `max_connections`(~87) → 여유 충분,
  변경 불필요(부하에서 병목은 커넥션이 아니라 CPU였음).

## 실행 재현
```
BASE_URL=https://d2pedv974beobb.cloudfront.net PEAK_VUS=4 k6 run perf/k6/spatial_load.js
```
task_cpu 라이브 변경: `describe-task-definition geuneul:<rev>` → `.cpu="512"` → `register-task-definition` →
`update-service --force-new-deployment`(deploy.yml은 describe 기반이라 이후 이미지 배포에도 cpu=512 보존).
EXPLAIN 인덱스 튜닝(V18)은 `perf/explain/n9-me-activity-tuning.txt`.
