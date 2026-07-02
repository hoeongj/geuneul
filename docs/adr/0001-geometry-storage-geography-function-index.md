# ADR-0001. 좌표 저장은 geometry(4326), 거리 연산은 geography 함수 인덱스

- 상태: 승인 (2026-07-02)
- 관련: `V2__create_core_tables.sql`, `V3__geography_functional_index.sql`, `PlaceRepository`

## 문제(Context)

그늘의 간판 쿼리는 "내 위치에서 반경 N**미터** 안의 장소"와 "가장 가까운 화장실"이다.
PostGIS에서 이걸 푸는 방식이 갈린다:

- `geometry(Point, 4326)`에 `ST_DWithin`을 그대로 쓰면 **단위가 도(degree)** 라서
  "반경 800"이 800m가 아니라 800°가 된다. 서울 위도에서는 경도 1°≈88km, 위도 1°≈111km로
  축까지 달라 도 단위 근사는 원이 아니라 타원이 된다.
- `geography` 타입은 미터 단위·구면 계산이 기본이지만, Hibernate Spatial의 표준 매핑과
  bounds(뷰포트) 검색(`&&` + `ST_MakeEnvelope`)은 geometry가 자연스럽다.

## 결정(Decision)

**저장은 `geometry(Point,4326)` 단일 컬럼, 미터 거리 연산은 `geography(geom)` 캐스팅 + 함수 인덱스.**

```sql
CREATE INDEX idx_places_geom_geography ON places USING GIST (geography(geom));  -- V3
-- 쿼리 (반경): ST_DWithin(geography(geom), geography(<점>), :meters)
-- 쿼리 (kNN):  ORDER BY geography(geom) <-> geography(<점>)
-- 쿼리 (bounds): geom && ST_MakeEnvelope(...)  → V2의 GIST(geom) 인덱스
```

함수 인덱스는 **쿼리의 식이 인덱스의 식과 동일할 때만** 작동한다. 그래서 리포지토리 쿼리는
반드시 `geography(p.geom)` 형태를 유지한다(주석으로 고정). `geom::geography` 캐스팅 문법은
Spring Data 네이티브 쿼리 파서가 `:geography`를 파라미터로 오인하므로 함수형 표기를 쓴다.

## 검토한 대안(Alternatives)

| 대안 | 기각 이유 |
|---|---|
| geography 타입 컬럼으로 저장 | Hibernate Spatial 매핑·bounds 검색·JTS 왕복이 geometry보다 번거로움. 도시 스케일 서비스에서 이점이 부족 |
| geometry + 도 단위 근사(°로 환산한 반경) | 축별 왜곡(타원)·정확성 설명 불가. 면접에서 방어 불가능한 선택 |
| 애플리케이션 레벨 하버사인 필터링 | 전체 스캔 = 인덱스 무용. CLAUDE.md 원칙 4 위반 |
| geometry+geography 컬럼 이중화 | 저장 중복·동기화 비용. 함수 인덱스로 동일 효과 |

## 결과(Consequences)

- 반경·kNN이 **미터 단위로 정확**하고 GiST 인덱스를 완전하게 탄다 (P4에서 EXPLAIN으로 실증 예정).
- 인덱스가 2개(geometry용, geography용) → 쓰기 비용 소폭 증가. 읽기 중심 서비스라 수용.
- 표시용 거리(`distanceM`)는 이미 DB가 선별·정렬한 결과에 하버사인 근사로만 계산(역할 분리).

## 근거(References)

- Paul Ramsey (PostGIS core), [Spatial Indexes and Bad Queries](http://blog.cleverelephant.ca/2021/05/indexes-and-queries.html) — geography 함수 인덱스 패턴의 출처
- [PostGIS: ST_DWithin](https://postgis.net/docs/ST_DWithin.html) · [geometry_distance_knn(`<->`)](https://postgis.net/docs/geometry_distance_knn.html)
- [PostGIS Workshop: Geography](http://postgis.net/workshops/postgis-intro/geography.html)
