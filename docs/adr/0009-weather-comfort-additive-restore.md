# ADR-0009. survival_score comfort — 기온(체감) 신호를 comfort_score 안에 additive로 복원

- 상태: 승인 (2026-07-09)
- 관련: `HeatComfort`, `WeatherService.getComfortScore`, `SurvivalScore.of(..., weatherComfort)`,
  `PlaceResponse.of(ScoredPlaceView, Double, Double)`, `PlaceSearchService`, `RecommendationService`
- 선행: [ADR-0007](0007-survival-score-sql-signals-java-compose.md)(survival_score 재정규화),
  [ADR-0008](0008-recommendations-scenario-weighted-ranking.md)(시나리오 가중 재사용),
  P3 날씨 1부(PR #26, 기상청 초단기실황 + Redis TTL 캐시)

## 문제(Context)

ADR-0007은 survival_score의 결측 성분(open_now·기온)을 "가짜 값 대신 재정규화로 제외"했다. 그중
open_now는 여전히 `open_hours_json`이 결측이라 제외 상태를 유지해야 하지만, **기온은 P3 날씨 1부**
(`GET /weather?lat=&lng=` — 기상청 초단기실황 + Redis TTL 캐시, 라이브)로 이미 실데이터가 있다.
CLAUDE.md §5 comfort_score 정의("시원함/앉을 곳/콘센트/물/화장실")에 원래 "시원함"이 들어있었으므로,
기온을 comfort의 한 성분으로 복원하는 것은 §5 원 스펙으로 돌아가는 additive 복원이다.

세 가지 설계 제약이 있었다(오케스트레이터 지시):

1. **KMA API N+1 절대 금지** — 날씨는 지역 신호라 장소마다 부르면 안 되고, 요청(쿼리)당 요청 좌표 기준 1회만.
2. **additive 복원** — §5 가중치 구조(distance/comfort/freshness/risk = 0.25/0.20/0.20/−0.15)를 깨지 않고,
   기온 신호를 **comfort_score 안의 한 성분**으로 더한다(5번째 최상위 가중치를 신설하지 않는다).
3. **SurvivalScore는 순수 함수 유지** — 날씨는 인자로 주입, I/O(WeatherService 호출)는 서비스 레이어에서.

## 결정(Decision)

### 1) 체감온도 계산 — 기상청 2022 개정 여름철 공식 그대로 사용

2026-07 웹 검색으로 확인한 결과, 기상청은 2022-06-02부터 여름철 체감온도를 다음 공식으로 산출한다
(공공데이터포털 "기상청_체감온도(여름철)" 문서 기준). 습구온도(Tw)는 Stull(2011) 근사식으로
**기온·습도만으로** 계산 가능해, 우리가 이미 가진 `Weather.temperatureC`(T1H)·`Weather.humidityPct`(REH)
로 바로 적용할 수 있었다(풍속 데이터 불필요 — 겨울 체감온도 산출식과 달리 여름 공식은 습구온도 기반):

```
Tw = Ta·atan[0.151977·(RH+8.313659)^0.5] + atan(Ta+RH) - atan(RH-1.67633)
     + 0.00391838·RH^1.5·atan(0.023101·RH) - 4.686035
체감온도 = -0.2442 + 0.55399·Tw + 0.45535·Ta - 0.0022·Tw² + 0.00278·Tw·Ta + 3.0
```

공식은 기온 20℃ 이상 구간에서 검증됐으므로, `HeatComfort.feelsLikeC`는 20℃ 미만이거나 습도가
결측이면 원 기온을 그대로 쓴다(어차피 25℃ 이하는 쾌적 상한 1.0으로 고정돼 정밀도가 결과를 바꾸지 않는다).

### 2) comfort 매핑 — 2026 기상청 폭염특보 체감온도 임계값에 앵커

또 다른 웹 검색(2026-07)으로 확인한 2026년 개편된 폭염특보 체감온도 기준을 매핑 앵커로 그대로 썼다
(추측값 대신 공식 기준선 — CLAUDE.md §0-B "방어 가능한 선택" 원칙):

| 체감온도 | 특보 단계 | comfort |
|---|---|---|
| ≤25℃ | (쾌적) | 1.0 |
| 33℃ | 폭염주의보 | 0.4 |
| 35℃ | 폭염경보 | 0.2 |
| 38℃ | **폭염중대경보**(2026 신설) | 0.0 |

구간 사이는 선형보간, 38℃ 초과는 0.0 고정. 강수 중(PTY≠0)이면 이동 중 불쾌감을 반영해 균일 −0.15
페널티를 얹는다. **"실내 선호 가중"(카테고리별 차등)은 이번 스코프에서 구현하지 않는다** — 장소의
실내/실외 여부를 판별할 `place_features` 데이터가 아직 없어(공부공간 확장 ADR-0006 대기 중), 정밀한
카테고리 가중은 그 데이터가 붙은 뒤 확장점으로 남긴다. 지금은 전역 소폭 조정으로만 반영해 과설계를 피했다.

### 3) comfort_score 내부 서브 조립 — 제보(0.6) · 날씨(0.4) 가중평균

`SurvivalScore.of(..., Double weatherComfort)`가 새 파라미터를 받는다. `weatherComfort`가 있으면:

```
comfort_score = (0.6·reportComfort + 0.4·weatherComfort) / (0.6+0.4)
```

`weatherComfort=null`(날씨 조회 실패·미설정)이면 기존과 동일하게 `reportComfort` 그대로 — **기존 4-인자
오버로드는 그대로 두고, 5-인자 오버로드를 추가**했다(기존 호출부·테스트 무변경 회귀 보장).
서브 가중치 0.6/0.4은 "제보는 이 장소 자체에 대한 증거(에어컨·그늘 등), 날씨는 주변 대기 상태라는
보조 신호"라는 원칙으로 제보를 우선한다. §5 최상위 가중치(distance/comfort/freshness/risk)는 그대로다
— comfort라는 "성분" 하나의 **내부** 조립만 바뀐다(제약 2 충족).

등급(UNKNOWN/GOOD/OKAY)은 여전히 `reportCount`로만 결정된다 — 날씨가 좋다고 제보 0건 장소가
GOOD/OKAY로 승격되지 않는다(ADR-0007 등급 규칙 불변, "정보 부족"의 의미는 여전히 UGC 부재를 뜻한다).

### 4) 날씨 주입 지점 — 서비스 레이어에서 요청당 1회

`WeatherService.getComfortScore(lat, lng)`(신규, `getWeather` + `HeatComfort.comfortScore` 위임)를
각 서비스가 **쿼리 실행 전후로 딱 한 번** 호출해 그 값을 배치 전체에 공통 적용한다:

- `PlaceSearchService.searchRadius` — 요청 lat/lng 기준 1회.
- `PlaceSearchService.searchBounds` — **bounds 중심(centroid)** 기준 1회(뷰포트에 단일 중심점이 없어
  west/south/east/north의 중점을 씀 — KMA 격자 해상도(~5km)에 비해 뷰포트가 보통 더 좁아 근사 타당).
- `PlaceSearchService.getById` — 그 장소 좌표 기준 1회(장소가 하나라 N+1 여지 자체가 없음).
- `RecommendationService.recommend` — 요청 lat/lng 기준 1회, 후보 풀(최대 200) 전체에 공통 적용.
- `PlaceSearchService.searchNearest`(nearest 팬아웃)는 애초에 survival을 계산하지 않는 경로라 날씨도 호출 안 함.

각 경로는 단위테스트(`PlaceSearchServiceTest`·`RecommendationServiceTest`, Mockito)로
"결과 건수와 무관하게 `WeatherService` 호출 1회"를 못 박았다(제약 1 충족, N+1 회귀 방지).

### 5) 실패 시 우회 — 기존 인프라 재사용

`WeatherService.getWeather`는 이미 실패(키 미설정·네트워크 장애·캐시 장애)를 `Optional.empty()`로
삼킨다(P3 날씨 1부 설계, `RedisCacheConfig.errorHandler` 포함). 이번 PR은 그 위에 `Optional.map`으로
`HeatComfort.comfortScore`를 얹기만 했으므로 **graceful degradation을 새로 만들 필요가 없었다** —
`weatherComfort=null`이 자연히 흘러 기존(기온 성분 제외) 점수로 폴백한다. 500을 낼 경로가 없다.

## 검토한 대안(Alternatives)

| 대안 | 기각 이유 |
|---|---|
| **§5에 5번째 최상위 가중치(예: temperature 0.1) 신설** | 지시된 제약(제약 2) 위반. comfort_score가 이미 "시원함"을 포함하는 개념이라(§5 원문) 별도 성분보다 comfort 내부 조립이 원 스펙에 더 충실 |
| **날씨를 comfort와 50:50로 동등 블렌드** | UGC(제보)는 "이 장소 자체"의 증거인 반면 날씨는 지역 평균 신호 — 특정 장소가 에어컨이 세거나 그늘이 깊으면 주변 기온과 체감이 다를 수 있다. 제보를 우선(0.6)하고 날씨는 보조(0.4)로 두는 게 더 방어 가능 |
| **NWS Rothfusz 회귀(전체 보정항 포함)** | 미국 화씨 기반+RH 극단값 보정 다항식으로 한국 상황·℃ 단위와 안 맞고 과설계. 기상청이 이미 공식 발표한 한국형 여름 체감온도 공식(우리 관측 소스가 애초에 기상청)이 있어 그걸 그대로 쓰는 게 더 방어 가능(같은 기관 데이터·같은 기관 공식) |
| **장소마다 날씨 호출(정확한 마이크로 격자)** | 제약 1 정면 위반 — KMA rate limit + N+1. 격자 해상도(~5km)상 실익도 없음(반경 검색 800m~5km, bounds 뷰포트가 대개 격자보다 좁음) |
| **강수 시 카테고리별 실내 선호 가중(도서관↑ 공원↓ 등)** | `place_features`에 실내/실외 플래그가 없어 이번 스코프에서 정밀 구현 불가. 균일 페널티로 최소 반영하고 확장점으로 문서화(과설계 대신 §0-B "지어내지 않는다" 원칙 재적용) |
| **weatherComfort를 필수 파라미터로 강제(기존 오버로드 제거)** | 기존 호출부·테스트가 전부 깨짐. 오버로드 추가(레거시 위임)로 무회귀 확장이 가능해 채택 |

## 결과(Consequences)

- survival_score comfort_score가 이제 "제보 + 날씨"를 함께 반영 — 폭염일 때 같은 제보라도 종합 점수가
  낮아지고(예: 쾌적 23℃ vs 폭염 38℃, IT로 확인), 쾌적할 때는 반대로 높아진다.
- 등급(마커 3색)은 여전히 reportCount로만 결정 — "정보 부족(UNKNOWN)"의 의미가 날씨로 흐려지지 않는다.
- 날씨 조회 실패는 기존 인프라(`Optional.empty()` 체인)로 자동 흡수 — 새 장애 모드 없음.
- `place_features`(실내/실외)가 붙으면 강수 페널티를 카테고리별로 정밀화할 확장점 확보(추가 스코프 필요).
- 다음 자연스러운 확장: `rain` 추천 시나리오(ADR-0008)의 risk 성분에도 **실시간** 강수(PTY)를 반영하는 것 —
  지금은 UGC FLOOD/SLIPPERY 제보만 risk에 반영되고 날씨 API의 실시간 강수는 아직 안 쓰인다(범위 확대 전 별도 제안 필요, CLAUDE.md §0-2).

## 근거(References)

- 체감온도 공식(2022-06-02 개정): 공공데이터포털 "기상청_체감온도(여름철)" —
  `체감온도 = -0.2442 + 0.55399·Tw + 0.45535·Ta - 0.0022·Tw² + 0.00278·Tw·Ta + 3.0`
  (습구온도 Tw는 Stull 근사식). [data.go.kr](https://www.data.go.kr/data/15043582/fileData.do)
- 2026년 폭염특보 체계 개편(폭염중대경보 신설, 체감 38℃): "체감온도 38도 넘으면 폭염중대경보"…기상청,
  올여름 특보 강화. [뉴시스](https://www.newsis.com/view/NISX20260512_0003626246)
- 폭염주의보(33℃)·폭염경보(35℃) 기존 기준 재확인: 기상청 2026년 여름철 방재기상대책 보도.
- 공식·제약: CLAUDE.md §4(날씨·Redis TTL 캐시 필수)·§5(survival_score comfort_score)·§7(AI/데이터 소스),
  ADR-0007(재정규화 원칙)·ADR-0008(가중치 오버로드 재사용 패턴)
