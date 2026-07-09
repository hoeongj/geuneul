# perf/ — 간판 성능 실증 (k6 부하테스트 + EXPLAIN 인덱스 튜닝, P4)

CLAUDE.md §10 P4·§11(간판=PostGIS 대용량 지리검색)의 성능 실증 자산. 결정·분석은
[docs/adr/0010](../docs/adr/0010-k6-load-explain-index-tuning.md), 실행계획 결과는
[explain/RESULTS.md](explain/RESULTS.md).

```
perf/
├── seed/seed_synthetic_places.sql       # 부하용 합성 대량 좌표(기본 30만 places + 1만 활성 제보)
├── explain/
│   ├── explain_spatial_queries.sql      # PlaceRepository 쿼리 1:1 EXPLAIN ANALYZE
│   └── RESULTS.md                        # 인덱스 사용 확증 + V8 before/after
└── k6/spatial_load.js                    # 반경·kNN·bounds·추천 부하 시나리오(p95/p99 threshold)
```

## 🔴 방침
- **부하는 로컬 docker-compose(PostGIS)에만** 건다. 프로덕션 ALB 고부하 금지(CLAUDE.md).
- 로컬 PostGIS 이미지는 amd64라 arm64 맥에서 **qemu 에뮬레이트** → 절대 지연(ms)은 부풀려짐. 읽을 값은
  **실행계획(인덱스 사용)**, **before/after 비율**, **동일 환경 처리량 상한/포화 곡선**. 프로덕션 수치는 추정 금지.

## 재현

```bash
# 1) 인프라
docker compose up -d                       # PostGIS + Redis
# (compose 하위 명령이 안 먹는 도커면: docker-compose up -d)

# 2) 백엔드 (Flyway가 V1~V8 적용)
cd backend && ./gradlew bootRun

# 3) 대량 시드 (별도 셸) — <pg>는 postgres 컨테이너 이름(docker ps로 확인)
docker exec -i <pg> psql -U geuneul -d geuneul -v n=300000 -f - < perf/seed/seed_synthetic_places.sql

# 4) EXPLAIN — GiST 인덱스 사용 확인
docker exec -i <pg> psql -U geuneul -d geuneul -f - < perf/explain/explain_spatial_queries.sql

# 5) 부하테스트 (k6 설치: brew install k6)
k6 run perf/k6/spatial_load.js                       # 기본 PEAK_VUS=4
k6 run --vus 1 --duration 5s perf/k6/spatial_load.js # 스모크
PEAK_VUS=8 k6 run perf/k6/spatial_load.js            # 부하 상향(환경별 조절)
BASE_URL=http://host:8080 k6 run perf/k6/spatial_load.js  # 원격(주의: 프로덕션 금지)
```

## 핵심 결과 (요약)
- 반경/kNN/bounds/추천 모두 **GiST 인덱스**를 탄다(EXPLAIN 확증). kNN(`<->`)이 가장 빠르고 포화에 강함.
- **Flyway V8** `idx_reports_expires`: 만료 제보 누적 시 `place_report_signals` 뷰의 전체스캔을 Bitmap Index
  Scan으로 전환 → 뷰 빌드 256→133ms, 반경 스코어드 361→172ms(만료 20만 시나리오).
- k6(green, PEAK_VUS=4): kNN p95 213ms · bounds p95 493ms · 반경 p95 1.4s · 추천 p95 1.24s, 실패율 0%.
