-- PostGIS 확장 활성화 — 반경(ST_DWithin)·최근접(kNN <->) 공간검색의 전제.
-- postgis/postgis 이미지에는 확장이 설치돼 있고, 여기서 DB에 활성화만 한다(멱등: IF NOT EXISTS).
CREATE EXTENSION IF NOT EXISTS postgis;
