# ADR-0019. 루트(Routes) — 화장실 포함 경로: 우리 POI 오버레이 + 외부 directions API

- 상태: 승인(MVP 구현 반영, 2026-07-10). 도로 폴리라인·그늘/비 경로는 follow-up.
- 관련: `domain.route`(신규), `PlaceRepository.findBestToiletWaypoint`(공간쿼리), ADR-0001(geography GIST),
  ADR-0004(BFF 프록시), CLAUDE.md §0-2(과설계 금지)·§6(공포 조장 금지), TS-026(외부 API 계약검증)

## 문제(Context)

로드맵 심화 B2: ① 화장실 포함 경로, ② 그늘 경로, ③ 비 피하는 경로. 자체 라우팅(pgRouting=도로망 데이터
대공사)은 과설계(§0-2). 두 질문: (1) **경유지를 무엇으로 고르나**, (2) **도로 폴리라인을 무엇으로 그리나**.

## 결정(Decision)

### 1) 경유지 선택 = 우리 PostGIS(간판) — detour 최소 화장실

"출발→화장실→도착"의 **우회(detour) 최소 화장실**을 우리가 고른다. midpoint 기준 corridor(`ST_DWithin`,
V3 GIST 함수 인덱스)로 후보를 좁힌 뒤 `(ST_Distance(출발)+ST_Distance(도착))` 합이 최소인 화장실 1곳을
DB에서 뽑는다(`findBestToiletWaypoint`). corridor 반경 = 직선거리/2 + 1.5km 버퍼(살짝 벗어난 화장실도 후보).
화장실이 corridor에 없으면 경유지 없이 직선 폴백. **이 부분이 그늘의 간판(대용량 공간검색)이라 우리가 소유한다.**

### 2) 도로 폴리라인 = 외부 directions API(카카오모빌리티), 지금은 직선 MVP

도로를 따르는 실제 경로는 **외부 경로 API**가 현실적(자체 도로망 라우팅은 §0-2 과설계). 2026 웹 확인:
**카카오모빌리티 다중경유지 길찾기**(`POST https://apis-navi.kakaomobility.com/v1/waypoints/directions`,
REST 키, 경유지 최대 100·총 1,500km, 월/일 쿼터) — 국내 도보/자동차 경로에 적합. **단, 활용신청·키·실호출
계약검증(TS-026)이 붙는 사용자 액션**이라 지금 shipping하지 않는다.

대신 **폴리라인 공급자를 전략(strategy)으로 추상화**한다(`DirectionsProvider`):
- **`StraightLineDirectionsProvider`(MVP, 기본 빈)**: 경유지를 그대로 이어 직선 폴리라인(`mode="straight"`).
  도로를 따르진 않지만 경유 여부·순서·근사 거리는 정확하다(경유지 선택은 PostGIS). 키 없이 동작·테스트된다.
- **Kakao 공급자(follow-up)**: 키 활성화 + TS-026 실호출 계약검증 후 이 인터페이스를 구현한 빈을 얹으면
  `RouteService` 변경 없이 `mode="road"`로 승격된다. **미검증 외부 계약을 지금 코드로 심지 않는다**(storeapi
  전례 — 승인 후 실측 정정, TS-026). 비밀 키는 `.local`/SSM(§D).

### 3) 그늘/비 경로 = 스코프만 기록(follow-up)

경로상 그늘 POI 밀도·강수 가중 라우팅은 난도 높고 자체 가중 라우팅은 §0-2 위반 위험. **단순화**(경로 주변
그늘/실내 POI를 오버레이 표시) 또는 후속 설계로 남긴다. 이번 MVP는 화장실 포함 경로만.

## 대안(Alternatives)

- **pgRouting 자체 도로망 라우팅**: 도로망 데이터 적재·유지 대공사, §0-2 과설계. 기각.
- **OSRM 자가호스팅**: 인프라·도로망 데이터 부담. 관리형 외부 API(카카오모빌리티)가 MVP엔 저비용. 후속 옵션.
- **미검증 Kakao 클라이언트를 바로 shipping**: TS-026 위반(외부 계약을 실호출 검증 없이 신뢰). 전략 추상화 +
  직선 MVP로 분리. 기각.
- **경유지도 외부 API에 위임**: 화장실 선택은 우리 간판(PostGIS)이라 우리가 소유하는 게 차별점. 외부엔 폴리라인만.

## 결과(Consequences)

- **좋음**: 키 없이 "출발→경유 화장실→도착" end-to-end 동작(직선 MVP, 테스트됨). 경유지 선택은 간판(PostGIS).
  키가 붙으면 `DirectionsProvider` 빈 교체만으로 `mode="road"` 승격(RouteService 불변). 표현 §6 순화.
- **비용**: 경유지 쿼리 1개(corridor 선필터라 인덱스 경로, 저비용).
- **한계(follow-up)**: 도로 폴리라인(Kakao 키·TS-026 계약검증)·지도 폴리라인 오버레이 UI·그늘/비 경로 미구현.
  프론트는 지금 경유 화장실·총거리를 안내(BFF `/api/routes/toilet` + 상세 "화장실 들러 가기").
