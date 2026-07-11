# ADR-0024. 그늘/비 경로 — 경로 corridor 주변 그늘·실내 피난처 오버레이(F4)

- 상태: 승인(구현 반영, 2026-07-11)
- 관련: F3(화장실 경로, ADR-0021)·B2(경로 MVP, ADR-0019), `RouteService`·`PlaceRepository.findShadeAlongCorridor`,
  `RouteResponse.shadeSpots`, 프론트 `RouteMiniMapLive`, SPEC.md §0-2(자체 라우팅 지양)·§3(루트=심화)
- 후속: BACKLOG N8

## 문제(Context)

F3에서 "화장실 들러 가기" 경로(출발→경유 화장실→도착 폴리라인)를 만들었다. F4의 요구는 "더울 때(그늘)·비 올 때
피할 곳"을 경로 위에서 보여주는 것. 그런데 **자체 가중 라우팅**(그늘을 우선하도록 경로를 재계산)은 SPEC.md §0-2가
지양하는 과설계다 — 우리 간판은 지리검색이지 라우팅 엔진이 아니다.

## 결정(Decision)

**라우팅을 바꾸지 않고, 기존 경로 폴리라인 주변의 그늘/실내 피난처를 PostGIS corridor로 조회해 오버레이한다.**

- `RouteService`가 만든 폴리라인을 `LINESTRING(경도 위도, ...)` WKT로 만들고, `PlaceRepository.findShadeAlongCorridor`가
  `ST_DWithin(geography(p.geom), geography(ST_GeomFromText(line)), 400m)`로 **경로에서 400m 이내**의
  **쉼터·도서관·지하상가**(COOLING_SHELTER·LIBRARY·UNDERGROUND)를 라인까지 가까운 순 15개 조회한다.
- 이 쿼리는 V3 `GIST(geography(geom))` 함수 인덱스를 선필터로 타 대용량에서도 인덱스 경로 유지(간판 정합).
- 응답 `RouteResponse.shadeSpots`(additive 필드)로 실어 보내고, 프론트 `RouteMiniMapLive`가 카테고리 마커로 오버레이,
  배너/토스트에 "피할 곳 N" 표기.

## 근거(Why)

- **왜 corridor 오버레이인가(자체 라우팅 대신)** — §0-2. 경로는 기존 대로(F3 도로/직선) 두고, "주변에 피할 곳이 있다"는
  정보만 얹는다. 구현·유지가 단순하고 간판(PostGIS 공간검색)을 그대로 재사용한다.
- **왜 쉼터·도서관·지하상가인가** — 셋 다 **실내/지붕** 피난처라 **그늘(폭염)과 비(장마)를 동시에** 해결한다("그늘/비 경로").
  음수대·공원 같은 야외는 비를 못 피하므로 제외.
- **왜 400m·15개인가** — 도보 우회 가능 범위(약 5분) + 작은 미니맵 혼잡 방지. 상수로 조정 가능.
- **왜 폴리라인 LINESTRING corridor인가(중점 원 대신)** — F3 화장실은 중점 원 corridor로 충분했지만(경유지 1곳 선택),
  F4는 경로 **전 구간** 주변을 봐야 하므로 라인 기준 거리(ST_DWithin line)가 정확하다. WKT는 좌표 doubles로만 만들고
  파라미터 바인딩이라 인젝션 안전, `Locale.ROOT`로 소수점 '.' 고정(WKT 파싱 안전).

## 결과(Consequences)

- 마이그레이션 0(기존 places·인덱스 재사용). `RouteResponse`에 `shadeSpots` 추가(additive, 기존 계약 불변).
- "그늘/비 경로"가 별도 화면이 아니라 기존 화장실 경로에 얹혀 하나의 "피난 경로" 경험으로 통합된다.
- 확장점: 카테고리·반경을 파라미터화하면 "비 전용(실내만)" vs "그늘 우선(공원 그늘 포함)" 모드로 분기 가능.
