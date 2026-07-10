# ADR-0017. 시설(place_features) → survival_score comfort 통합 (A1 "간판 정밀화")

- 상태: 승인(구현 반영, 2026-07-10)
- 관련: `V13__place_feature_signals_view.sql`(신규 뷰), `SurvivalScore`(단조 상승 조립), `ScoredPlaceView`·
  `PlaceRepository`(4개 스코어드 쿼리 LEFT JOIN), ADR-0007(place_report_signals 시공간 신호 — SQL 집계+Java 조립),
  ADR-0009(날씨 comfort additive), ADR-0005 §④(FeatureGrade 등급화·표시), CLAUDE.md §5(survival_score)·§9(마커 3색)

## 문제(Context)

`place_features`(에어컨·콘센트·wifi·좌석·음수대·study_ok·quiet·소음 등)는 지금까지 상세 화면의 **등급 칩**
(`FeatureGrade`, ADR-0005 §④)으로 "표시"만 되고 **survival_score에는 전혀 들어가지 않았다**. 그 결과:

- **냉방 쉼터·콘센트 카페가 "무제보"이면 comfort=0**으로 취급돼 종합점수·추천 랭킹에서 정적 시설의 이점을 못 받았다.
  "여름 실내 오래 버티기"의 간판 신호(에어컨·좌석)가 스코어에 안 실리는 건 간판의 정밀도 손실이다.
- 반대로 정적 시설을 무제한 반영하면 **저신뢰 PUBLIC 백필 feature가 실시간 UGC(제보) comfort를 덮을** 위험이 있고,
  §9("마커 3색 흔들림 최소")를 깨서 지도가 요란해질 수 있다.

두 질문: (1) 시설 신호를 **어디서** 집계하나, (2) 제보/날씨 comfort와 **어떻게** 합치되 UGC를 안 덮고 마커를 안 흔드나.

## 결정(Decision)

### 1) 집계 = 별도 SQL 뷰 `place_feature_signals`(간판 = DB 시공간·시설 집계)

시설 comfort는 **DB에서** 장소별 `feature_comfort[0,1]`로 집계한다(ADR-0007 정신모델 유지 — "무거운 집계는 SQL,
가중 조립은 Java"). `place_report_signals`(제보)와 **별도 뷰**인 이유: 그 뷰는 `reports`를 GROUP BY라 "제보 있는
장소"만 행이 있는데, 시설은 "제보 없는 장소"에도 붙어 같은 뷰에 넣으면 FULL OUTER JOIN 재구성이 필요하다. 두 신호를
각각 뷰로 두고 4개 스코어드 쿼리(반경/카테고리반경/bounds/단건)가 **둘 다 LEFT JOIN**(제보/시설 없으면 COALESCE 0)
하면 DRY하고 인덱스 경로도 그대로다.

**polarity·confidence 가중**(A1 함정 대응):
- 긍정 불리언 시설(에어컨/좌석/음수대/화장실/study_ok/quiet/no_eyes, truthy) → `confidence × 0.5`
- 등급 시설(콘센트/wifi): many/high/fast=강(0.5)·some/medium/ok=중(0.3)·few/low/slow=약(0.15) → `× confidence`
- 부정 시설(noise_level=loud) → `confidence × 0.3`만큼 차감. §6대로 "위험"이 아니라 comfort 감소로만 표현.
- `feature_comfort = clamp(Σ긍정 − Σ부정, 0, 1)`. 성분당 상한 0.5라 **단일 시설이 comfort를 포화시키지 못한다**
  (에어컨 하나로 "완벽히 쾌적"은 아니다). confidence NULL은 0.5(중립). PUBLIC 백필은 confidence를 낮게(0.3~0.6)
  심어 UGC를 못 덮는다.

뷰가 `FeatureGrade`(Java, 표시용)와 truthy/polarity 규약을 **의도적으로 이중 표현**한다 — 표시(칩)와 스코어(집계)는
관심사가 달라 각자 최적 표현을 쓰고, 실 PostGIS IT로 뷰 시맨틱을 확증한다(로컬 skip≠통과, CI 게이트, TS-009).

### 2) 조립 = 제보/날씨 base 위에 시설의 **단조 상승**(회귀 없음·UGC 우선·마커 안정)

`SurvivalScore.effectiveComfort`가 순서대로:
1. **base** = 제보·날씨 블렌드(ADR-0009 그대로: 날씨 있으면 0.6·제보+0.4·날씨, 없으면 제보). 시설 없으면 여기서 끝나
   **기존 동작과 100% 동일**(폴백 회귀 없음 — WeatherComfortIT·기존 단위테스트 불변).
2. **시설 상승** = `base + (1−base)·feature_comfort·GAIN`(GAIN=0.5). 시설은 comfort를 **올리기만** 한다:
   - **무회귀·마커 안정(§9)**: 내리지 않으므로 시설 추가가 등급을 떨어뜨리지 않는다. verified 가중(V10)과 같은 단조 철학.
   - **UGC 우선(A1 함정)**: 체감(감소 수익) 구조라 이미 base가 높은(=UGC 강한) 장소는 거의 안 움직인다 → "PUBLIC
     시설이 UGC를 덮지 않게".
   - 부정 시설은 뷰에서 이미 긍정과 상쇄돼 `feature_comfort`에 반영되므로(상승폭이 그만큼 준다) 여기서 또 감점하지
     않는다 — 단조성을 지키기 위한 의도적 단순화.

### 3) 등급(마커 3색)은 여전히 **실시간 신호(reportCount)** 로만 결정 — 시설은 등급을 못 바꾼다(무제보→UNKNOWN 유지)

`comfort` 수치는 시설로 오르지만, **UNKNOWN↔GOOD/OKAY 경계는 reportCount로만** 판정한다(ADR-0007·ADR-0009 계승).
근거: 등급 = "**지금** 상태를 아는가"(live). 에어컨·콘센트는 정적 사실이지 "지금 시원하다는 최근 신호"가 아니다.
무제보 냉방쉼터를 초록으로 칠하면 "지금 좋다는 제보가 있다"는 거짓 함의가 생기고, 14만 장소가 일제히 색이 바뀌어
§9("흔들림 최소")를 정면으로 깬다. 대신 **제보가 있는 장소**에서는 시설이 comfort를 올려 OKAY→GOOD로 승격시킬 수
있다(수용 기준 "comfort 성분↑로 등급 상승"을 이 경로로 충족) — 실시간 신호가 이미 있는 장소의 정밀화이므로 안전하다.
추천(/recommendations) 랭킹은 등급과 무관하게 `feature_comfort`가 반영된 matchScore를 쓰므로, 무제보 냉방쉼터도
"rest30"에서 더 위로 올라온다 — 간판 정밀화의 실효는 여기서 난다.

## 대안(Alternatives)

- **Java에서 장소별 place_features 로딩 후 조립**: 반경/bounds는 수백 장소 배치라 N+1 또는 대형 IN 조인이 필요 →
  간판(DB 시공간 집계) 철학과 어긋나고 느리다. 배치 경로엔 뷰 LEFT JOIN이 정답. 기각.
- **기존 place_report_signals 뷰에 features FULL OUTER JOIN**: 제보 없는 장소 행 생성 문제로 뷰가 복잡·취약해진다.
  두 신호를 관심사별로 분리한 별도 뷰가 단순·안전. 기각.
- **시설이 등급까지 승격(무제보 냉방쉼터=OKAY)**: §9 위반·거짓 함의(정적 사실을 "지금 신호"로 오인). 기각.
- **가중평균(reportCount 게이팅) 방식**: 무제보 시 시설·날씨만 평균 → 깔끔하나 "시설 추가가 comfort를 내릴 수도"
  있어(예: 낮은 feature_comfort가 높은 날씨를 희석) 회귀 위험. 단조 상승이 §9·무회귀에 더 안전. 기각.

## 근거·트렌드(Rationale)

2단(retrieval → weighted re-rank) 스코어링에서 "정적 속성은 단조 부스트, 실시간 신호는 등급 게이트"로 분리하는 것은
검색·추천의 표준 관행(정적 feature는 랭킹 가산점, freshness/live-signal은 별도 게이트). ADR-0007/0008/0009가 세운
"SQL 집계 + Java 가중 조립 + 결측 성분 재정규화" 계보에 additive하게 얹었고, verified(V10)의 단조 보너스 철학과 일치한다.

## 결과(Consequences)

- **좋음**: 냉방·콘센트 등 간판 시설이 무제보라도 종합점수·추천에 반영(간판 정밀화). 마커 색은 불변(§9). 폴백 회귀 0.
  A3(쉼터 냉방 백필)이 붙으면 냉방쉼터 comfort↑가 실동작한다.
- **비용**: 스코어드 쿼리에 뷰 LEFT JOIN 1개 추가(place_features는 소규모라 무해, 대량 트래픽 튜닝은 P4 EXPLAIN).
- **한계(후속)**: 부정 시설만 있는 장소가 base 아래로 내려가진 않는다(단조성 우선). 정적 시설의 comfort 감점이 꼭
  필요해지면 별도 risk 채널로 확장(현재는 §6·§9 우선으로 보류).
