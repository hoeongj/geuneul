# ADR-0007. survival_score — 시공간 신호는 SQL(뷰), 최종 가중치 조립은 순수 함수

- 상태: 승인 (2026-07-04)
- 관련: `V4__place_report_signals_view.sql`, `SurvivalScore`, `ScoredPlaceView`, `PlaceRepository`, `PlaceResponse`, 프론트 `lib/survival.ts`·`marker.ts`
- 선행: [ADR-0001](0001-geometry-storage-geography-function-index.md)(geography 함수 인덱스), [ADR-0005](0005-cafe-features-as-summer-scenario.md)(SEAT_OK·CROWDED 흡수)

## 문제(Context)

간판 = "PostGIS 대용량 지리검색 + **실시간 UGC 시공간 스코어링**"인데(CLAUDE.md 간판 헤드라인),
지리검색(P1)·제보 수집(P2)까지는 라이브였지만 **점수(survival_score) 자체가 미구현**이었다.
프론트에는 마커 링·상태 배지가 "회색 고정(정보 부족)" 예약 슬롯으로만 있었다.

CLAUDE.md §5는 공식과 최근성 버킷을 확정해 준다:

```
survival_score = 0.25·distance + 0.20·open_now + 0.20·comfort + 0.20·freshness − 0.15·risk
freshness 버킷: 0~1h=1.0 | 1~3h=0.8 | 오늘=0.6 | 이번주=0.3 | 그 외=0.1
```

그리고 "**시공간 랭킹은 DB(PostGIS/SQL) 레이어에서 계산하는 것을 지향한다**"(§5), "제보는 **trust_score로 가중**",
"후기(review)는 survival_score와 **분리**"라는 제약을 준다. 두 개의 설계 질문이 남는다:

1. 점수 계산을 **어디서** 하나 — 전부 SQL? 전부 Java? 섞나?
2. 우리 실제 데이터에 **없는 성분**(open_now·place_features)을 어떻게 다루나 — 가짜 값? 제외?

## 결정(Decision)

**하이브리드: 무거운 시공간 집계는 SQL 뷰가, 문서화된 가중치 조립·등급은 순수 함수가 담당한다.**

### 1) SQL 뷰 `place_report_signals` — 시공간 집계 (V4)

장소별 **유효(미만료) 제보**를 최근성 버킷 × 신뢰도 가중으로 집계해 3개 신호를 만든다:

- `freshness_score` = `MAX(freshness_weight)` — 가장 신선한 유효 제보의 최근성(순수 recency, 0~1)
- `comfort_score` = `LEAST(SUM(긍정 제보의 freshness×trust), 1.0)` — 시원/자리/물/화장실
- `risk_score` = `LEAST(SUM(부정 제보의 freshness×trust×severity), 1.0)` — 더움/붐빔/벌레/냄새/침수/미끄럼

`WHERE expires_at > now()`로 만료 제보를 제외(휘발성 규약). 신뢰도 가중 = 익명 0.7 기저,
로그인 유저는 `trust_score`(0~100)로 0.7→1.0 가산(현재 전량 익명이라 실질 0.7, P2 로그인 시 자동 반영).
안전 리스크(FLOOD·SLIPPERY) severity=1.0, 체감 리스크 0.6(§6 "공포 조장 금지" — 리스크는 최대 −0.15만 감점).

각 공간쿼리(반경/bounds/단건)는 이 뷰를 **LEFT JOIN** 하고 `COALESCE(...,0)`로 제보 없는 장소를 0 신호로 만든다.
공간 인덱스 경로(ADR-0001)는 스코어드 쿼리에서도 그대로 유지된다.

### 2) 순수 함수 `SurvivalScore` — 가중치 조립 + 등급

SQL이 준 신호(+ 거리)를 §5 가중치로 조립하는 **DB 없이 단위테스트되는** 결정 로직:

- **결측 성분 재정규화**: `open_now`는 공공데이터 운영시간이 사실상 전부 결측이라 **성분에서 제외**(가짜 값 금지).
  `distance`는 중심점이 있는 반경 검색에서만 반영. → `base = Σ(wᵢ·sᵢ)/Σ(wᵢ)`(가용 긍정 성분의 가중평균).
  `score = clamp(base − 0.15·risk, 0, 1)×100`.
- **거리 의미 분리**: bounds(마커)·단건은 거리 성분을 빼고 "**장소 자체가 지금 좋은가**"를 계산(내 거리와 무관).
  반경/최근접만 거리 0.25를 넣어 "**지금 갈만함**" 랭킹.
- **등급(마커 3색)**: 유효 제보 0건 → `UNKNOWN`(회색·정보 부족). 있으면 score≥60 `GOOD`(초록) / 그 외 `OKAY`(노랑).
  → "정보 부족 회색"이 대다수(제보 없는 46k 화장실)인 것은 **정직한 라이브 상태 제품**의 올바른 기본값.

후기(review)는 이 파이프라인에 **넣지 않는다**(§5 분리 — 휘발성 상태 ≠ 영구 평판).

## 검토한 대안(Alternatives)

| 대안 | 기각 이유 |
|---|---|
| **점수 전부를 SQL 한 방에** | 재정규화·등급 분기까지 네이티브 쿼리에 넣으면 가독성·테스트성 급락. 가중치는 자주 튜닝할 "정책"이라 순수 함수가 맞음 |
| **점수 전부를 Java(앱)에서** | 장소마다 제보를 로딩해 최근성 집계 = N+1·전체스캔. 간판("시공간 랭킹은 DB에서", §5) 정면 위반 |
| open_now를 중립값 0.5로 채움 | 없는 데이터를 지어내는 것. 면접에서 방어 불가. 재정규화로 "모르면 빼기"가 정직 |
| 마커를 거리 포함 점수로 칠하기 | 뷰포트 마커는 중심점이 없고, "먼 곳이 회색"은 상태가 아니라 거리 착시. 마커=장소 상태로 분리가 옳음 |
| 상관 서브쿼리(장소당) vs 뷰 | 뷰가 report 로직을 한 곳에 모아 3쿼리가 DRY. 제보는 소규모 UGC라 성능 충분. 대량 시 튜닝은 P4(k6+EXPLAIN) |
| 빨강(위험) 버킷 추가 | §6 "공포 조장 금지". 침수도 노랑"주의"로 — 3색(초록/노랑/회색) 유지 |

## 결과(Consequences)

- 간판 미구현분(실시간 UGC 시공간 스코어링) **완성** — 마커 3색·상태 배지가 라이브 제보로 굴러간다.
- 스코어링 정책(가중치·임계·severity)이 순수 함수 1곳에 모여 **8개 단위테스트**로 고정. 튜닝이 안전.
- 뷰가 전 제보를 집계 → 트래픽 급증 시 튜닝 여지(부분 인덱스·집계 캐시)는 **P4에서 additive**하게. 지금은 과설계 금지.
- `open_hours_json`·`place_features`가 채워지면(P2/P3) open_now·시설 comfort를 **가중치 복원만으로** 붙일 확장점 확보.
- 신뢰도 가중이 SQL에 이미 있어 P2 로그인이 붙으면 **코드 변경 없이** trust가 점수에 반영된다.

## 근거(References)

- 트렌드 확인(2026-07): 시간감쇠 스코어링은 **"SQL 레이어=대용량 성능, 앱 레이어=복잡·유연 로직"** 트레이드오프가
  정설 — 본 설계(시공간 집계=SQL, 정책 조립=순수 함수)가 그 절충의 표준형.
  [Exponentially decaying likes](https://julesjacobs.com/2015/05/06/exponentially-decaying-likes.html) ·
  [Data Freshness vs Latency (Tacnode, 2025)](https://tacnode.io/post/data-freshness-vs-latency)
- PostGIS 스코어드 조인도 `&&`/`ST_DWithin` 인덱스 선필터 후 집계 — [Crunchy Data: PostGIS Performance Indexing & EXPLAIN](https://www.crunchydata.com/blog/postgis-performance-indexing-and-explain)
- 공식·제약: CLAUDE.md §5(survival_score)·§6(공포 조장 금지)·§8(ERD reports/reviews 분리)
