# EXPLAIN ANALYZE 결과 — PostGIS 공간쿼리 인덱스 실증 (P4)

환경: 로컬 docker-compose PostGIS 16-3.4 (colima 2 vCPU, **amd64 이미지를 arm64에서 qemu 에뮬레이트** — 절대 시간은 부풀려짐, 계획 형태·before/after 비율이 유효). 데이터: `perf/seed/seed_synthetic_places.sql`로 places 30만 + reports 21만(유효 1만·만료 20만).
재현: 아래 "재현" 절 + `perf/explain/explain_spatial_queries.sql`.

## 1. 마커 쿼리별 인덱스 사용 (핵심 증빙)

| 쿼리 | 리포지토리 메서드 | 실행계획 핵심 노드 | 인덱스 |
|---|---|---|---|
| 반경(ST_DWithin geography) | `findWithinRadiusScored` | `Bitmap Index Scan on idx_places_geom_geography` (Index Cond: `geography(geom) && _st_expand(...,800)`) | ✅ GiST(geography) |
| kNN 최근접(`<->`) | `findNearest` | `Index Scan using idx_places_geom_geography` + KNN 정렬 (LIMIT 5, cost 상한 5.1M이지만 actual 30ms — KNN 인덱스가 상위 5개만 방문) | ✅ GiST(geography) KNN |
| kNN + 카테고리 | `findNearest(category=TOILET)` | `Index Scan using idx_places_geom_geography` + `Rows Removed by Filter` | ✅ GiST(geography) KNN |
| bounds(geometry &&) — 선택적 박스 | `findInBoundsScored` | `Bitmap Index Scan on idx_places_geom` (Index Cond: `geom && envelope`) | ✅ GiST(geometry) |
| bounds — 밀집 대박스 + LIMIT | `findInBoundsScored` | `Seq Scan` (조기종료) — 아래 주석 | (의도된 플래너 선택) |
| 추천(카테고리 집합) | `findWithinRadiusScoredByCategories` | `BitmapAnd(idx_places_geom_geography, idx_places_category)` | ✅ GiST + btree |
| 단건 상세 | `findByIdScored` | `Index Scan using idx_places_active` (id 조건) | ✅ 부분 인덱스 |

**bounds가 대박스에서 Seq Scan을 고르는 이유(버그 아님):** `LIMIT 100` + `ORDER BY` 없음 + 시드의 70%가 수도권에 몰려 있어, 수도권 큰 박스는 첫 ~9천 행 안에서 100건이 다 차므로 플래너가 조기종료 Seq Scan을 정답으로 고른다. **선택적(희소 지역·작은) 박스에서는 `idx_places_geom`(geometry GiST)을 실제로 탄다**(강원 산간 박스 실측: Bitmap Index Scan on idx_places_geom, 1.7ms). 즉 인덱스는 "이득이 될 때" 정확히 쓰인다.

## 2. 2026-07-18 결정적 30만 snapshot 재검증

ADR-0030 후속으로 DB seed와 fingerprint를 고정한 현재 스키마(V1~V20)를 다시 측정했다. 환경은 Colima 2 vCPU/4GiB,
Docker 29.5.2(aarch64), amd64 PostGIS 16-3.4 QEMU, k6 2.1.0이다. 데이터는 샘플 10+합성 300,000 places와
활성 합성 reports 10,000이며, `data_seed=0.20260718` fingerprint는
`f45d466de919f4ed573997ce5e7c7d03:37f362ee091648caea99535eefe68aa0`이다. 수도권 bbox 비율은 70.17%였다.

| 쿼리 | 핵심 계획 | Execution Time |
|---|---|---:|
| 반경 800m | `Bitmap Index Scan on idx_places_geom_geography` | 298.919ms |
| kNN 5개 | `Index Scan using idx_places_geom_geography` | 20.310ms |
| kNN + TOILET | `Index Scan using idx_places_geom_geography` | 19.644ms |
| 밀집 bounds + LIMIT 100 | `Index Scan using idx_places_active` 조기종료 | 66.024ms |
| 추천 반경 + 카테고리집합 | `BitmapAnd(idx_places_geom_geography, idx_places_category)` | 193.828ms |
| 단건 상세 | `Index Scan using idx_places_active` | 0.994ms |

밀집 bounds가 이번에는 과거 Seq Scan 대신 active partial index를 골랐지만 의미는 같다. 결과의 큰 비율을 포함하는
박스에서 100행만 필요하므로 geometry bitmap을 만드는 것보다 먼저 일치하는 행에서 멈추는 계획이 싸다. 선택적 bounds의
geometry GiST 근거는 위 기존 실측을 유지한다. reports가 전부 활성인 이번 snapshot에서는 reports Seq Scan이 정상이다.
아래 V8 결과는 만료 20만/활성 1만이라는 별도 누적 시나리오에서만 비교한다.

## 3. V8 튜닝 — place_report_signals 뷰의 유효 제보 필터 (before/after)

스코어드 쿼리(반경/bounds/추천)는 매번 `place_report_signals` 뷰를 LEFT JOIN 한다. 뷰는 `WHERE r.expires_at > now()`로 유효 제보만 집계하는데, 제보는 휘발성이라 만료 행이 계속 누적된다. 플래너는 GROUP BY를 통과해 place_id를 밀어넣지 못하므로 뷰는 **매 쿼리마다 통째로** 집계된다 → 인덱스가 없으면 이 필터가 reports 전체를 Seq Scan.

**실측(places 30만 + reports 21만 중 유효 1만·만료 20만):**

| 대상 | V8 이전 | V8 이후 | 계획 변화 |
|---|---|---|---|
| 뷰 빌드(`SELECT * FROM place_report_signals`) | **256 ms** | **133 ms** | `Seq Scan on reports (Rows Removed by Filter: 200000)` → `Bitmap Index Scan on idx_reports_expires (expires_at > now())` |
| 반경 스코어드(광화문 800m) | **361 ms** | **172 ms** | 위 뷰 서브트리 동일 전환 (공간 GiST 경로는 before/after 동일) |

V8 = `CREATE INDEX idx_reports_expires ON reports (expires_at)`. `now()`는 IMMUTABLE이 아니라 부분 인덱스 predicate에 못 넣으므로 전체 btree로 범위스캔(`expires_at > now()`)을 태운다. 유효 제보 비율이 낮을수록(=프로덕션 누적 시나리오) 이득이 커진다.

## 재현
```
docker compose up -d
cd backend && ./gradlew bootRun            # Flyway V1~V20 적용
# 대량 시드(별도 셸)
docker exec -i <postgres> psql -U geuneul -d geuneul \
  -v n=300000 -v data_seed=0.20260718 -f - < perf/seed/seed_synthetic_places.sql
# 실행계획
docker exec -i <postgres> psql -U geuneul -d geuneul -f - < perf/explain/explain_spatial_queries.sql
```
