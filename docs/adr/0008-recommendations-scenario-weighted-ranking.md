# ADR-0008. 추천(/recommendations) — survival_score에 시나리오 가중을 얹은 2단 랭킹

- 상태: 승인 (2026-07-05)
- 관련: `RecommendationController/Service/Scenario/Reason`, `RecommendationResponse`, `SurvivalScore.Weights`, `PlaceRepository.findWithinRadiusScoredByCategories`, 프론트 `app/api/urgent/route.ts`·`components/urgent/*`
- 선행: [ADR-0007](0007-survival-score-sql-signals-java-compose.md)(survival_score), [ADR-0001](0001-geometry-storage-geography-function-index.md)(geography 함수 인덱스), [ADR-0005](0005-cafe-features-as-summer-scenario.md)(SEAT_OK·CROWDED)

## 문제(Context)

survival_score(P3, ADR-0007)까지 라이브가 됐고, CLAUDE.md §9의 남은 P3 조각은
`GET /recommendations?scenario=rest30|restroom|rain` — "지금 30분 버틸 곳 / 화장실 급할 때 / 비 피할 곳"이다.
HANDOFF이 명시한 "콘솔 없이 바로 가능한 다음"이자, survival_score에 **시나리오 가중을 얹는 자연스러운 다음 조각**.

기존 프론트 "급해요"는 `/api/urgent` 프록시가 **카테고리별 nearest(kNN)를 팬아웃해 거리순 병합**하는 근사였다.
이는 (1) 순수 거리 정렬이라 실시간 상태(붐빔·침수·시원함)를 전혀 안 보고, (2) survival_score를 못 쓰며,
(3) 랭킹 로직이 프론트에 흩어져 있었다. 세 가지 설계 질문:

1. 시나리오별 랭킹을 **어디서** 계산하나 — 기존 nearest 팬아웃 유지? 새 엔드포인트?
2. 시나리오 차이를 **어떻게** 표현하나 — 완전히 다른 공식? 가중치만?
3. 조밀한 카테고리(화장실 46k)에서 "가까운데 무제보" vs "먼데 좋은 제보"를 어떻게 다루나?

## 결정(Decision)

**백엔드에 `/recommendations`를 신설하고, survival_score와 같은 순수 조립 함수를 재사용하되 시나리오별 가중치만 바꾼다.
검색은 2단(retrieval → re-rank): PostGIS 공간 인덱스가 후보를 선필터하고, 앱이 후보 풀을 시나리오 가중으로 재랭킹한다.**

### 1) 시나리오 = (카테고리 집합, 가중치 프로파일)

`RecommendationScenario` enum이 둘을 소유한다:

| 시나리오 | 카테고리 | distance | comfort | freshness | risk | 의도 |
|---|---|---|---|---|---|---|
| **rest30** 잠깐 쉬어갈 곳 | 쉼터·도서관·지하상가·공공기관·공원 | 0.25 | **0.35** | 0.20 | 0.25 | 시원하게 오래 앉을 곳 → comfort↑ |
| **restroom** 화장실 급함 | 화장실 | **0.60** | 0.10 | 0.10 | 0.15 | 급함 → distance 압도 |
| **rain** 비 피할 곳 | 도서관·지하상가·공공기관·쉼터 | 0.35 | 0.15 | 0.15 | **0.40** | 침수·미끄럼 회피 → risk 페널티↑ |

(§5 표준 배지 가중치는 0.25/0.20/0.20/−0.15.)

### 2) 조립은 survival_score 순수 함수 재사용 — 가중치만 파라미터화

`SurvivalScore.of(Weights, ...)` 오버로드를 추가했다. 조립식(`base = Σ(wᵢ·sᵢ)/Σ(wᵢ)`,
`score = clamp(base − w_risk·risk, 0,1)×100`)·등급 규칙은 지도 배지와 **완전히 동일**하고 가중치만 다르다.
→ 스코어링 정책이 한 곳(`SurvivalScore`)에 모여 튜닝·단위테스트가 안전. 시공간 신호(freshness/comfort/risk)는
여전히 SQL 뷰 `place_report_signals`(ADR-0007)가 계산한다.

응답은 각 장소에 **두 점수**를 담는다(의도적 분리):
- `place.survival` — §5 표준 가중치 "이 장소의 지금 상태"(**지도 마커와 동일 배지**, 어디서나 일관).
- `matchScore` — 시나리오 가중치 "이 상황 적합도"(**이 목록의 정렬 기준**) + `reason`(실시간 제보 요약).

### 3) 2단 검색 — 공간 인덱스 선필터 → 후보 풀 재랭킹

`findWithinRadiusScoredByCategories`가 시나리오 카테고리 안에서 반경 내 **가까운 순 상위 pool**을 뽑고
(ST_DWithin geography + KNN 정렬, ADR-0001 인덱스 경로 유지), 앱이 그 풀을 matchScore로 재정렬한다.
pool은 limit×5(최소 50, 최대 200) — 거리순 선필터가 "가깝지만 붐빔"을 위로 올려도 재랭킹이 뒤집을 재료를 확보하고,
상한이 재랭킹 비용을 묶는다. 이는 2025 표준 **retrieval-then-rerank**(coarse 후보 → context-aware 재랭킹) 패턴 그대로다.

이로써 "화장실 급함"에서 **가까운 무제보 화장실이 먼 유제보 화장실을 이기고**(distance 0.60),
"비 피할 곳"에서 **신선한 침수 제보 장소가 같은 거리의 멀쩡한 곳보다 아래로 강등**된다(risk 0.40).

## 검토한 대안(Alternatives)

| 대안 | 기각 이유 |
|---|---|
| **기존 nearest 팬아웃 유지** | 순수 거리 정렬이라 실시간 상태를 못 봄. survival_score(간판)를 안 씀. 랭킹이 프론트에 흩어짐 |
| **시나리오마다 별도 공식** | 조립·등급 로직 중복 → 튜닝·테스트 급락. "가중치만 다른 같은 공식"이 DRY하고 방어 쉬움 |
| **matchScore 없이 survival만으로 정렬** | 시나리오 무관 단일 랭킹이 됨("급함"에서도 시원함이 거리를 이겨버림). 상황별 가중이 요점 |
| **단일 점수만 노출(배지=랭킹)** | 배지가 시나리오마다 바뀌어 같은 장소가 지도와 달라 보임. 배지=장소상태 / matchScore=적합도 분리가 정직 |
| **rain risk도 §6대로 −0.15로 순화** | 비 피난은 사용자가 **명시적으로 침수를 피하려는** 상황. 랭킹 강등(순위 하향)은 빨간 경고 라벨이 아니라 §6와 양립. 배지는 여전히 −0.15로 순화 유지 |
| **SQL 한 방에 시나리오 CASE로 랭킹** | 가중치는 자주 튜닝할 "정책"이라 순수 함수가 맞음(ADR-0007과 동일 논리). 후보는 소규모라 앱 재랭킹으로 충분 |
| **카테고리를 프론트가 계속 소유** | 랭킹이 백엔드로 오면서 카테고리 집합도 백엔드가 소유해야 단일 진실. 프론트 SCENARIO_META는 표시만 |

## 결과(Consequences)

- P3 추천 시나리오 **완성** — "급해요" 3탭이 거리+실시간 상태를 시나리오 가중으로 랭킹(라이브 승격).
- 스코어링 정책이 `SurvivalScore` 한 곳에 유지 — 배지와 추천이 같은 함수를 공유, 가중치만 데이터. 튜닝 안전.
- 2단 검색이 공간 인덱스 경로(ADR-0001)를 그대로 타 성능 회귀 없음. 대량 트래픽 시 pool·집계 튜닝은 **P4(k6+EXPLAIN)**.
- `place_features`(콘센트/와이파이/좌석)가 채워지면 comfort 성분에 **가중치 복원만으로** 시나리오 정밀화 가능(확장점).
- 신뢰도 가중이 SQL에 이미 있어(ADR-0007) P2 로그인이 붙으면 추천 랭킹에도 **코드 변경 없이** trust가 반영.

## 근거(References)

- 트렌드 확인(2026-07): **retrieval → re-ranking**(coarse 후보 검색 → context/scenario-aware 재랭킹)은
  검색 랭킹의 표준 2단 구조 — 후보를 top-K로 선필터하고 재랭커가 후보별 근거로 재점수한다.
  [Contextual Retrieval & Context-Aware Ranking (EmergentMind)](https://www.emergentmind.com/topics/contextual-retrieval-and-context-aware-ranking)
- 공간 선필터 후 재랭킹도 인덱스(&&/ST_DWithin) 선필터가 정석 — [Crunchy Data: PostGIS Performance Indexing & EXPLAIN](https://www.crunchydata.com/blog/postgis-performance-indexing-and-explain)
- 공식·제약: CLAUDE.md §5(survival_score)·§6(공포 조장 금지)·§9(recommendations API)
