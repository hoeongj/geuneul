# perf/ — 간판 성능 실증 (k6 부하테스트 + EXPLAIN 인덱스 튜닝, P4)

docs/SPEC.md §10 P4·§11(간판=PostGIS 대용량 지리검색)의 성능 실증 자산. 결정·분석은
[docs/adr/0012](../docs/adr/0012-k6-load-explain-index-tuning.md), 실행계획 결과는
[explain/RESULTS.md](explain/RESULTS.md).

```
perf/
├── seed/seed_synthetic_places.sql       # 부하용 합성 대량 좌표(기본 30만 places + 1만 활성 제보)
├── explain/
│   ├── explain_spatial_queries.sql      # PlaceRepository 쿼리 1:1 EXPLAIN ANALYZE
│   └── RESULTS.md                        # 인덱스 사용 확증 + V8 before/after
├── k6/spatial_load.js                    # 반경·kNN·bounds·추천 부하 시나리오(p95/p99 threshold)
└── results/                               # 실행별 machine-readable JSON(파일은 gitignore)
```

## 🔴 방침
- **부하는 로컬 docker-compose(PostGIS)에만** 건다. 프로덕션 ALB 고부하 금지(docs/SPEC.md). 스크립트도
  loopback이 아닌 `BASE_URL`을 기본 차단한다.
- 로컬 PostGIS 이미지는 amd64라 arm64 맥에서 **qemu 에뮬레이트** → 절대 지연(ms)은 부풀려짐. 읽을 값은
  **실행계획(인덱스 사용)**, **before/after 비율**, **동일 환경 처리량 상한/포화 곡선**. 프로덕션 수치는 추정 금지.

## 재현

```bash
# 1) 인프라
docker compose up -d                       # PostGIS + Redis
# (compose 하위 명령이 안 먹는 도커면: docker-compose up -d)

# 2) 백엔드 (Flyway가 V1~V20 적용)
cd backend && ./gradlew bootRun

# 3) 대량 시드 (별도 셸) — <pg>는 postgres 컨테이너 이름(docker ps로 확인)
docker exec -i <pg> psql -U geuneul -d geuneul \
  -v n=300000 -v data_seed=0.20260718 -f - < perf/seed/seed_synthetic_places.sql

# 4) EXPLAIN — GiST 인덱스 사용 확인
docker exec -i <pg> psql -U geuneul -d geuneul -f - < perf/explain/explain_spatial_queries.sql

# 5) 부하테스트 (k6 설치: brew install k6)
DATA_SEED=0.20260718 DATA_FINGERPRINT=<places-hash>:<reports-hash> \
  RUN_SEED=20260718 k6 run perf/k6/spatial_load.js
DATA_SEED=0.20260718 DATA_FINGERPRINT=<places-hash>:<reports-hash> \
  PEAK_VUS=8 RUN_SEED=20260718 k6 run perf/k6/spatial_load.js

# 입력/summary helper 단위 테스트 + k6 init 계약 검사
node --test perf/k6/spatial_config.test.js
k6 inspect perf/k6/spatial_load.js
```

기본 실행은 `data_seed=0.20260718`, `RUN_SEED=20260718`, `PEAK_VUS=4`, stages `20s/40s/10s`다. SQL은
places/reports fingerprint를 출력하며, 같은 `(seed, VU, iteration)`은 같은 좌표·카테고리·반경·시나리오를 만든다.
종료 시 `perf/results/spatial-summary.json`에 schema version, 요청/DB seed, DB fingerprint, stages/target,
요청·실패율, 엔드포인트별 p90/p95/p99, threshold 결과가 기록된다. 경로는 `SUMMARY_PATH=/tmp/result.json`으로
바꿀 수 있다.

원격 테스트가 정말 필요한 별도 비운영 환경은 `ALLOW_REMOTE_LOAD=true`를 명시해야 하지만, 이 저장소 방침상
프로덕션 대상에는 사용하지 않는다.

## same-condition p95 비교 경계

요청/DB seed와 fingerprint를 고정해도 p95 회귀 비교는 PEAK_VUS/stages, 앱 빌드, 인덱스, PostGIS 버전,
머신/컨테이너 자원, k6 버전이 모두 같을 때만 유효하다. JSON은 seed/fingerprint/stages/target을 기록하지만 빌드 SHA와
하드웨어는 실행 기록에 별도로 남겨야 한다. 동일 조건이어도 OS 스케줄링과 시스템 잡음 때문에 latency와 총 iteration
수 자체는 결정적이지 않다([ADR-0030](../docs/adr/0030-ingest-operational-ledger-deterministic-load.md)).

## 핵심 결과 (요약)
- 반경/kNN/추천 공간 선필터는 **GiST 인덱스**를 탄다. bounds는 선택적 박스에서 geometry GiST를 쓰고, 밀집 박스는
  `LIMIT 100` 조기종료가 더 싸면 active index나 Seq Scan을 선택한다. kNN(`<->`)이 가장 빠르고 포화에 강하다.
- **Flyway V8** `idx_reports_expires`: 만료 제보 누적 시 `place_report_signals` 뷰의 전체스캔을 Bitmap Index
  Scan으로 전환 → 뷰 빌드 256→133ms, 반경 스코어드 361→172ms(만료 20만 시나리오).
- 2026-07-18 결정적 로컬 기준(Colima 2 vCPU/4GiB, amd64 PostGIS 16-3.4 QEMU, k6 2.1.0, peak 4 VU,
  places 30만+샘플 10, reports 1만): **반경 p95 1,349.02ms · kNN 73.31ms · bounds 357.41ms · 추천
  918.64ms**, 672/672 성공, 9.55 req/s, 모든 p95/p99 임계 통과. DB fingerprint는
  `f45d466de919f4ed573997ce5e7c7d03:37f362ee091648caea99535eefe68aa0`이다. 외부 날씨 키는 비워 추천이
  graceful fallback을 사용했다.
- 과거 기준(ADR-0012, peak 4 VU)은 kNN p95 213ms · bounds 493ms · 반경 1.4s · 추천 1.24s, 실패율 0%다.
  앱 변경·DB snapshot·k6 버전이 달라 두 실행 사이 개선률은 계산하지 않는다.
