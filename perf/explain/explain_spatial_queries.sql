-- P4 EXPLAIN ANALYZE — 세 공간쿼리(반경/kNN/bounds) + 스코어드(뷰 조인)가 GiST 인덱스를 타는지 실증.
-- PlaceRepository.java의 네이티브 쿼리를 1:1로 옮겨(파라미터만 리터럴로 치환) 실행계획을 캡처한다.
-- 실행: docker exec -i <pg> psql -U geuneul -d geuneul -f - < explain_spatial_queries.sql
--
-- 확인 포인트:
--  · 반경/kNN: "Index Scan using idx_places_geom_geography" (GiST, geography 함수 인덱스, ADR-0001) —
--    Seq Scan이 아니어야 한다.
--  · bounds: "Index Scan/Bitmap ... idx_places_geom" (GiST, geometry && 박스검색).
--  · 스코어드: 위 공간 인덱스 선필터 + place_report_signals 뷰 조인(reports 집계).

\pset pager off
\timing on

\echo ========== 1. 스코어드 반경 검색 (findWithinRadiusScored) — 광화문, 800m ==========
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
       ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
       ST_Distance(geography(p.geom), geography(ST_SetSRID(ST_MakePoint(126.9769, 37.5759), 4326))) AS "distanceM",
       COALESCE(s.report_count, 0)   AS "reportCount",
       COALESCE(s.freshness_score, 0) AS "freshnessScore",
       COALESCE(s.comfort_score, 0)  AS "comfortScore",
       COALESCE(s.risk_score, 0)     AS "riskScore"
FROM places p
LEFT JOIN place_report_signals s ON s.place_id = p.id
WHERE p.deleted_at IS NULL
  AND ST_DWithin(geography(p.geom), geography(ST_SetSRID(ST_MakePoint(126.9769, 37.5759), 4326)), 800)
  AND (CAST(NULL AS text) IS NULL OR p.category = CAST(NULL AS text))
ORDER BY geography(p.geom) <-> geography(ST_SetSRID(ST_MakePoint(126.9769, 37.5759), 4326))
LIMIT 100;

\echo ========== 2. kNN 최근접 (findNearest) — 광화문, 5개 ==========
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
       ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
       ST_Distance(geography(p.geom), geography(ST_SetSRID(ST_MakePoint(126.9769, 37.5759), 4326))) AS "distanceM"
FROM places p
WHERE p.deleted_at IS NULL
  AND (CAST(NULL AS text) IS NULL OR p.category = CAST(NULL AS text))
ORDER BY geography(p.geom) <-> geography(ST_SetSRID(ST_MakePoint(126.9769, 37.5759), 4326))
LIMIT 5;

\echo ========== 3. kNN 최근접 + 카테고리 필터 (findNearest, category=TOILET) ==========
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
       ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
       ST_Distance(geography(p.geom), geography(ST_SetSRID(ST_MakePoint(126.9769, 37.5759), 4326))) AS "distanceM"
FROM places p
WHERE p.deleted_at IS NULL
  AND (CAST('TOILET' AS text) IS NULL OR p.category = CAST('TOILET' AS text))
ORDER BY geography(p.geom) <-> geography(ST_SetSRID(ST_MakePoint(126.9769, 37.5759), 4326))
LIMIT 5;

\echo ========== 4. 스코어드 bounds (findInBoundsScored) — 서울 도심 박스 ==========
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
       ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
       CAST(NULL AS double precision) AS "distanceM",
       COALESCE(s.report_count, 0)   AS "reportCount",
       COALESCE(s.freshness_score, 0) AS "freshnessScore",
       COALESCE(s.comfort_score, 0)  AS "comfortScore",
       COALESCE(s.risk_score, 0)     AS "riskScore"
FROM places p
LEFT JOIN place_report_signals s ON s.place_id = p.id
WHERE p.deleted_at IS NULL
  AND p.geom && ST_MakeEnvelope(126.90, 37.53, 127.05, 37.60, 4326)
  AND (CAST(NULL AS text) IS NULL OR p.category = CAST(NULL AS text))
LIMIT 100;

\echo ========== 5. 스코어드 반경 + 카테고리집합 (findWithinRadiusScoredByCategories, rest30 시나리오) ==========
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
       ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
       ST_Distance(geography(p.geom), geography(ST_SetSRID(ST_MakePoint(126.9769, 37.5759), 4326))) AS "distanceM",
       COALESCE(s.report_count, 0)   AS "reportCount",
       COALESCE(s.freshness_score, 0) AS "freshnessScore",
       COALESCE(s.comfort_score, 0)  AS "comfortScore",
       COALESCE(s.risk_score, 0)     AS "riskScore"
FROM places p
LEFT JOIN place_report_signals s ON s.place_id = p.id
WHERE p.deleted_at IS NULL
  AND ST_DWithin(geography(p.geom), geography(ST_SetSRID(ST_MakePoint(126.9769, 37.5759), 4326)), 2000)
  AND (CAST('LIBRARY,CIVIC,UNDERGROUND,COOLING_SHELTER' AS text) IS NULL
       OR p.category = ANY(string_to_array('LIBRARY,CIVIC,UNDERGROUND,COOLING_SHELTER', ',')))
ORDER BY geography(p.geom) <-> geography(ST_SetSRID(ST_MakePoint(126.9769, 37.5759), 4326))
LIMIT 20;

\echo ========== 6. 단건 상세 (findByIdScored) ==========
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT p.id AS "id", p.name AS "name", p.category AS "category", p.address AS "address",
       ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", p.source AS "source",
       CAST(NULL AS double precision) AS "distanceM",
       COALESCE(s.report_count, 0)   AS "reportCount",
       COALESCE(s.freshness_score, 0) AS "freshnessScore",
       COALESCE(s.comfort_score, 0)  AS "comfortScore",
       COALESCE(s.risk_score, 0)     AS "riskScore"
FROM places p
LEFT JOIN place_report_signals s ON s.place_id = p.id
WHERE p.id = 5 AND p.deleted_at IS NULL;
