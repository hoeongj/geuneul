-- 미터 단위 반경(ST_DWithin)·최근접(<->) 검색을 위한 geography 함수 인덱스 (ADR-0001).
--
-- 저장은 geometry(Point,4326) 유지(Hibernate Spatial 표준 매핑·bounds 검색용),
-- 거리 연산은 geography(geom) 캐스팅으로 미터 단위 정확 계산.
-- 함수 인덱스의 식과 쿼리의 식이 동일해야 인덱스를 타므로,
-- 리포지토리 쿼리도 반드시 geography(p.geom) 형태를 쓴다.
-- 참고: Paul Ramsey(PostGIS core), "Spatial Indexes and Bad Queries" (2021).
CREATE INDEX idx_places_geom_geography ON places USING GIST (geography(geom));
