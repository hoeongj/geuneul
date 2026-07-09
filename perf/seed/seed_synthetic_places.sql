-- P4 부하테스트용 합성 대량 좌표 시드 — Flyway 마이그레이션이 아니다(프로덕션에 적재 금지, CLAUDE.md 지시).
-- 로컬 docker-compose PostGIS에서만 psql로 직접 실행한다.
--
-- 목적: PostGIS GiST 인덱스(반경 ST_DWithin geography · kNN <-> · bounds &&)가 "실제로" 대용량에서
-- 인덱스를 타는지 EXPLAIN ANALYZE + k6로 실증하려면, 프로덕션 규모(공중화장실 46,897건 등)에 준하는
-- places 행 수가 필요하다. 로컬 시드(R__seed_sample_places.sql)는 10건뿐이라 옵티마이저가 사실상
-- 항상 Seq Scan을 선택해도 차이가 안 보인다(테이블이 작으면 Seq Scan이 합리적 선택이기도 하다).
--
-- 분포: 실제 공공데이터 분포(인구 밀집지 편중)를 근사하기 위해 70%는 수도권 bbox, 30%는 전국 bbox에
-- 균등분포시킨다 — "전국 그대로 적재, 데이터가 많을수록 대용량 지리검색 간판이 강해진다"(CLAUDE.md §3)
-- 원칙과 정합되게 nationwide 분포를 유지하면서도 실사용 쿼리(대부분 수도권에서 반경 800m~2km 검색)의
-- 국지성을 재현한다.
--
-- 재현: docker exec -i <postgres-container> psql -U geuneul -d geuneul -v n=300000 -f - < seed_synthetic_places.sql
-- (psql -v n=<개수>로 규모 조절. 기본값은 아래 \if 블록에서 300000.)

\if :{?n}
\else
  \set n 300000
\endif

\echo 합성 places 생성 시작 — n = :n

-- 기존 합성 데이터는 재실행 시 정리(idempotent 재현을 위해 delete-then-insert).
DELETE FROM places WHERE source = 'synthetic_loadtest';

-- 주의: 카테고리/좌표의 random()은 파생 서브쿼리(t)의 SELECT 목록에서 행마다 평가한다.
-- (CROSS JOIN LATERAL로 감싸면 outer 참조가 없어 플래너가 한 번만 평가·캐시해 전 행이 같은 값이 된다 — 실측으로 확인된 함정.)
INSERT INTO places (name, category, address, geom, source, source_external_id, geocoded, is_commercial, created_at, updated_at)
SELECT
    'synthetic-' || t.gs AS name,
    t.category,
    '합성 시드 — 좌표만 유효' AS address,
    ST_SetSRID(ST_MakePoint(t.lng, t.lat), 4326),
    'synthetic_loadtest',
    'synth-' || t.gs,
    false,
    t.category IN ('CAFE', 'STUDY_CAFE'),
    now(),
    now()
FROM (
    SELECT
        gs,
        -- TOILET을 프로덕션처럼 최다수 카테고리로(실제 46,897건이 무더위쉼터 100건보다 압도적으로 많음).
        (ARRAY['TOILET','TOILET','TOILET','WATER','PARK','LIBRARY','CIVIC','UNDERGROUND','COOLING_SHELTER','ETC'])
            [1 + floor(random() * 10)::int] AS category,
        CASE WHEN random() < 0.7
             -- 수도권 bbox (서울·경기·인천 근사): lng 126.6~127.3, lat 37.2~37.75
             THEN 126.6 + random() * 0.7
             -- 전국 bbox: lng 124.6~131.0
             ELSE 124.6 + random() * 6.4
        END AS lng,
        CASE WHEN random() < 0.7
             THEN 37.2 + random() * 0.55
             -- 전국 bbox: lat 33.1~38.6
             ELSE 33.1 + random() * 5.5
        END AS lat
    FROM generate_series(1, :n) AS gs
) t;

\echo 합성 places 완료
SELECT count(*) AS synthetic_places FROM places WHERE source = 'synthetic_loadtest';

-- 합성 제보(reports) — place_report_signals 뷰(V4/ADR-0007) 조인 비용을 실측하려면 UGC 볼륨도 필요.
-- 프로덕션은 "현재 모든 제보가 익명"(WORKLOG 2026-07-09)이라 user_id NULL로 맞춘다(회귀 없는 동일 조건).
DELETE FROM reports WHERE comment = 'synthetic-loadtest-report';

INSERT INTO reports (place_id, report_type, status_value, comment, is_anonymous, created_at, expires_at)
SELECT
    p.id,
    (ARRAY['COOL','HOT','BUG','ODOR','SMOKE','SEAT_OK','CROWDED','WATER_OK','RESTROOM_CLEAN'])
        [1 + floor(random() * 9)::int] AS report_type,
    NULL,
    'synthetic-loadtest-report',
    true,
    now() - (random() * interval '2 hours'),   -- freshness 버킷(0~1h/1~3h)이 섞이게
    now() + interval '2 hours'                  -- 아직 유효(만료 전) — 뷰 집계에 실제로 잡힘
FROM (
    SELECT id FROM places WHERE source = 'synthetic_loadtest' ORDER BY random() LIMIT LEAST(:n / 30, 15000)
) p;

\echo 합성 reports 완료
SELECT count(*) AS synthetic_reports FROM reports WHERE comment = 'synthetic-loadtest-report';

ANALYZE places;
ANALYZE reports;
\echo ANALYZE 완료 — 옵티마이저 통계 갱신
