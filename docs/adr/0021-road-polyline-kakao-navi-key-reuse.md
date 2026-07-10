# ADR-0021. 화장실 경로 도로 폴리라인 — 카카오내비 길찾기, 기존 REST 키 재사용

- 상태: 승인(구현 반영, 2026-07-11)
- 관련: ADR-0019(화장실 경로 MVP·`DirectionsProvider` 전략), `domain.route`, `KakaoGeocodingClient`(같은 키),
  프론트 `RouteMiniMap`/`PlaceDetailOverlay`, CLAUDE.md §0-4(공간쿼리)·§7(카카오)·TS-026(계약검증)
- 후속: BACKLOG F3(완료) → F4(그늘/비 경로)

## 문제(Context)

B2 화장실 경로(ADR-0019)는 직선 폴백(`mode=straight`)만 라이브였다. 실제 도로를 따르는 폴리라인은 외부
directions API가 필요한데, ADR-0019는 이를 "카카오모빌리티 활용신청·키(사용자 액션)"로 후속 분리했다.

확인해보니 **길찾기 API 신청 경로가 카카오 디벨로퍼스 콘솔에 안 보인다** — 길찾기는 별도 사이트
(카카오모빌리티 디벨로퍼스)에서 제공된다고 문서가 안내한다. 그런데 **엔드포인트는 기존 카카오 REST 키를
그대로 받는다**는 정황이 있어(카카오내비 길안내 API는 2023년 개방, KakaoAK 헤더), 실호출로 검증했다.

## 결정(Decision)

### 1) 기존 카카오 REST 키를 재사용한다(신규 발급 불필요) — 실호출 계약검증(TS-026)

지오코딩·로그인에 쓰는 그 키(`kakao.rest-api-key`, env `KAKAO_REST_API_KEY`, 이미 SSM 배선·프로덕션 라이브)로
`apis-navi.kakaomobility.com`을 실호출해 인가를 확인했다(2026-07-11):

- `GET /v1/directions` → HTTP 200, `result_code:0 길찾기 성공`, 도로 폴리라인 정점 83개
- `POST /v1/waypoints/directions`(다중경유지) → HTTP 200, `result_code:0`, 경유지 기준 구간 분할

→ **별도 카카오모빌리티 앱 등록·새 키 발급이 필요 없다.** 새 비밀·SSM 항목 0개. F3이 사용자 액션 없이 풀렸다.

### 2) 다중경유지 엔드포인트를 그대로 쓴다(우리 모델과 1:1)

우리 경로 = 출발 → (경유 화장실 1) → 도착. `POST /v1/waypoints/directions`의 origin/waypoints/destination에
그대로 매핑된다. 경유 화장실이 없으면 waypoints 빈 배열(직행). 응답
`routes[0].sections[].roads[].vertexes`(평탄 [경도,위도,…])를 순서대로 이어 폴리라인으로 만든다.

- **비용 주석**: 카카오모빌리티는 2026년 다중경유지에 건당 과금(할인가 ~10원)을 도입했으나, 포트폴리오
  트래픽에선 무시할 수준이고 단일경로 무료 쿼터(일 다수)도 있다. 비용이 문제되면 **단일경로 2-leg
  (출발→화장실, 화장실→도착)로 무료 대체** 가능 — `DirectionsProvider` 내부만 바꾸면 되는 확장점으로 남긴다.

### 3) 전략 교체는 `@Primary` + 내부 키 게이트 — 기존 계약·테스트 무변경

`KakaoDirectionsProvider`를 `@Primary`로 등록해 키가 있으면 `RouteService`가 이걸 쓴다(`mode=road`).
키가 없으면(로컬/CI) `polyline()`이 **HTTP 없이 즉시 empty** → RouteService가 직선 폴백(`mode=straight`).
런타임 실패(네트워크·쿼터·`result_code!=0`)도 empty → 직선 폴백(graceful). 그래서:

- `RouteService`·`RouteResponse`·기존 `RouteToiletIT`(mode=straight 기대) **무변경**으로 통과(CI엔 키 없음).
- ADR-0019가 예고한 "키 붙으면 빈으로 얹어 승격" 전략 교체점이 그대로 실현됐다.

`@ConditionalOnProperty` 대신 `@Primary`+키게이트를 쓴 이유: `kakao.rest-api-key`는 application.yml에 항상
정의(기본 "")돼 있어 조건 프로퍼티가 빈 문자열에도 매칭돼 판정이 애매하다. `@Primary`+런타임 키게이트가
결정적이다(빈 키면 항상 직선 폴백).

### 4) 프론트 — 상세 미니맵에 폴리라인 오버레이

"화장실 들러 가기" 시 응답 폴리라인을 상단 미니맵에 그린다(`RouteMiniMap`, react-kakao-maps-sdk `Polyline`
+ bounds fit). `mode=road`는 실선 파랑, `straight`는 점선 회색으로 구분하고 배지로 표기. 출발·경유 화장실·도착
마커. 키 없으면 지도는 숨기고 기존 텍스트 안내만(graceful).

## 대안(Alternatives)

- **카카오모빌리티 신규 앱 등록·별도 키**: 실호출로 기존 키가 인가됨을 확인해 불필요(새 비밀 0).
- **단일경로 2-leg 무료 조합**: 비용 회피용 확장점으로 문서화만(현재는 다중경유지 1콜이 단순).
- **자체 가중 라우팅(그늘 경로)**: §0-2 범위 밖 — F4에서 "경로 주변 쉼터 오버레이"로 단순화 예정.

## 결과(Consequences)

- (+) 사용자 액션·새 비밀 0으로 도로 폴리라인 라이브. 기존 키·간판 공간쿼리(경유지 선택) 재사용.
- (+) 키 없는 환경은 자동 직선 폴백 — 기존 계약·테스트·CI 무변경.
- (−) 다중경유지 과금 가능성 — 포트폴리오 규모엔 무시 가능, 필요 시 2-leg 무료 전환점 확보.
- (−) 카카오 응답 스키마 변화 리스크 — 타입 record 파싱 + 실패 시 직선 폴백으로 흡수(전면 장애 아님).
