# ADR-0006. 공부 가능 공간(공공 + 카페) 데이터 커버리지 확장 — "여름 실내 오래 버티기" survival 레이어

- 상태: **Accepted(구현 반영)** — 카테고리(CAFE/STUDY_CAFE)·스키마(is_commercial/deleted_at)·파서·인제스천 코드 완료(2026-07-09). **프로덕션 실적재는 미실행**(사용자 통제, `infra/scripts/prod-ingest.sh` 리뷰 후 실행). 상권정보(STUDY_CAFE/CAFE) 오픈API는 활용신청 미승인(403)이라 계약 미검증 상태로 코드만 준비됨 — 아래 "구현 정정" 참고.
- 관련: `PlaceCategory`, `place_features`, `SourceSpec`/`IngestionService`(idempotent ETL), `domain.ingest.openapi`(도서관)·`domain.ingest.storeapi`(상권정보) 신설, Flyway V5, ADR-0002(멱등)·ADR-0003(지오코딩), CLAUDE.md §3(커버리지 원칙)·§9(간판 vs 살)
- 근거 조사: 다중 에이전트 리서치(공공 공부공간 데이터셋·노들서가류·카페 데이터·모델링) — wf_e524daf8. 구현 단계에서 도서관 오픈API 실호출로 원안의 핵심 가정 하나를 정정함(아래).

## 구현 정정(2026-07-09) — 원안과 다르게 간 지점

원안(위 "결정" 섹션, 착수 시점 문서)은 소스별 접근 방식을 추정으로 정했으나, 실제 구현 중 **실 API 호출로 검증**한 결과 한 가지가 정정됐다(CLAUDE.md §B 의사결정 프로토콜 — 검증 후 재확정):

- **전국도서관표준데이터 = CSV 다운로드가 아니라 JSON 오픈API로 적재.** 원안은 "오픈API는 경기도만 제공, CSV 다운로드가 전국"이라 추정했다(HANDOFF 메모 근거). 그러나 `https://api.data.go.kr/openapi/tn_pubr_public_lbrry_api` 를 확보된 `.local/datago.env`의 `DATA_GO_KR_SERVICE_KEY`로 실호출한 결과 **지역 파라미터 없이 pageNo/numOfRows 페이지네이션만으로 전국 3,555건**을 반환했다(광주·서울 등 여러 시도 확인, `totalCount=3555`). CSV 다운로드보다 훨씬 단순하고(수동 파일 확보 불필요), 레코드마다 열람좌석수(`seatCo`)를 직접 제공해 ADR 원안의 "열람좌석수>0만 백필" 조건부 규칙을 CSV 경로의 균일 근사 없이 **그대로 정밀 구현**할 수 있었다(`domain.ingest.openapi.PublicLibraryIngestionService`).
- **상권정보(STUDY_CAFE/CAFE)는 계약 미검증.** 같은 계정 키로 상가업소정보 오픈API(`B553077/api/open/sdsc2`)를 호출했으나 **403(활용신청 미승인)** — 도서관 API와 달리 별도 승인 절차가 있다. 코드(`domain.ingest.storeapi`)는 공식 매뉴얼·서드파티 가이드 리서치를 근거로 준비했지만, 실 필드명·enum 값(divId 등)을 확증하지 못해 **승인 후 재검증이 필요한 상태로 남겨뒀다.** 그래서 행정동코드 열거가 필요한 `storeListInDong` 대신, 우리 PostGIS 반경검색과 동일한 정신모델인 **반경 검색(`storeListInRadius`)**을 택했다 — 행정동코드 목록(또 다른 데이터셋)을 몰라도 격자 좌표 순회로 전국을 커버할 수 있어 승인 후 즉시 쓸 수 있는 형태.

## 구현 결과 요약

- **스키마(V5)**: `places.is_commercial`(카테고리 파생값, `PlaceCategory.commercial()`) · `places.deleted_at`(soft-delete). 5개 공간쿼리(`PlaceRepository`)에 `deleted_at IS NULL` 필터 추가.
- **카테고리**: `PlaceCategory`에 `CAFE`·`STUDY_CAFE` 추가 + `commercial()` 판별 메서드.
- **소스**:
  - LIBRARY → `domain.ingest.openapi.PublicLibraryIngestionService`(JSON 오픈API, CLI `--ingest.source=library`, 파일 불필요). seatCo>0 조건부 study_ok/quiet 백필.
  - STUDY_CAFE/CAFE → `domain.ingest.storeapi.StoreIngestionService`(JSON 오픈API, CLI `--ingest.source=study_cafe|cafe --ingest.lat= --ingest.lng= --ingest.radius=`). ⚠️계약 미검증. `StoreCategoryMapper`가 업종소분류명 텍스트로 분류(코드 매핑표 15067631 실측 전까지 임시).
- **멱등·soft-delete**: `PlaceBulkUpsertRepository.deactivateStale()`(opt-in, 기본 false — 부분 스냅샷 재실행 사고 방지) + `backfillFeatures()`(ON CONFLICT DO NOTHING, UGC 값 보존). CSV 파이프라인(`IngestionService`)·도서관 오픈API 둘 다 재사용. 상권정보는 반경 단위 부분 스냅샷이라 soft-delete 의도적 미지원(전국 커버리지 완성 후 P3 무인화에서 다룸).
- **테스트**: 파서/클라이언트/서비스 단위 25건(신규) + 실 PostGIS IT 4건(`StudySpaceCoverageIT`, CI에서 검증 — 로컬 colima 이슈로 skip, TS-009 선례). JaCoCo 로컬 LINE 65.7%(신규 순수 단위테스트 다수) → floor 0.35→0.60 상향(측정치 바로 아래, 회귀만 차단).

## 문제(Context)

"공부 가능한 **카페** + 공공기관 중 공부 가능한 **공간**(예: 노들서가)을 전부 넣고 싶다"는 요구. 이는 UGC 기능이 아니라 **데이터 커버리지 확장**이며, CLAUDE.md §3 커버리지 원칙("공공데이터가 있는 곳 전부 적재")과 정합하고 **간판(PostGIS 대용량 지리검색 + idempotent ETL)을 직접 강화**한다. 다만 4가지 설계 리스크:
1. **정체성** — "여름 생존"이 "연중 카페 추천앱"으로 번질 위험(§9).
2. **카테고리 남발** — 종류마다 enum을 늘리면 지도 필터·색상만 복잡해짐.
3. **커먼스 희석** — 상업 POI(카페) 대량 유입이 "공개 커먼스" 성격을 흐림.
4. **'공부 가능'은 어떤 공공데이터에도 없음** — 카공맵 등 유사 서비스도 100% 유저 크라우드소싱.

## 결정(Decision)

### 1) 카테고리 최소 응집 + 속성 분리
- **`PlaceCategory`에 `CAFE`·`STUDY_CAFE` 2개만 신설.** "무슨 장소인가(kind)"만 category로.
- **"공부 가능한가"는 cross-cutting 속성** → `place_features`에 `study_ok`·`quiet` feature_type 추가. (enum 남발 회피 — 도서관·카페·공공공간 어디든 붙을 수 있는 축.)
- **상업 vs 커먼스 분리** → `places.is_commercial` 플래그. 커먼스(도서관·공공시설개방·명소)와 상업(카페)을 분리 노출·필터해 "공개 커먼스" 정체성 방어.

### 2) 소스 우선순위 (기존 멱등 ETL 재사용 최대화)
| 순위 | 소스 | → 카테고리 | 좌표 | 난이도 | 비고 |
|---|---|---|---|---|---|
| 1 | **전국도서관표준데이터**(15013109) | LIBRARY | WGS84 내장 | S | `StandardCsvParser` 그대로. 지오코딩 0. 작은도서관 포함. 열람좌석>0 → `study_ok`/`quiet` 백필 |
| 2 | **상권정보 독서실/스터디카페**(15083033) | STUDY_CAFE | WGS84 | M | 규모 1만대 — 전용 파서·업종필터·defaultFeatures 검증용. `study_ok` 자동부여 |
| 3 | **상권정보 커피전문점**(15083033) | CAFE | WGS84 | M | 약 9만. 지오코딩 0(WGS84라 화장실 6만 적재 규모 내). `study_ok`는 **UGC로만** |
| 4 | **전국공공시설개방정보표준데이터**(15013117) | CIVIC | 있음 | M | 개방 회의실·세미나실·열람실. 시설유형 화이트리스트 필터 |
| 5 | **명소 시드**(노들서가·서울책보고·무중력지대·SeSAC) | CIVIC/LIBRARY | 주소만 | S | 데이터셋 없음 → 수동 시드 CSV + 카카오 지오코딩(ADR-0003). 수동 feature 태깅 |
| — | ~~LOCALDATA 지방행정인허가~~ | 보조 | **EPSG:5174** | L | 좌표 비-WGS84라 재투영/지오코딩 부담 → 1순위 배제, 폐업 교차검증 보조만 |

### 3) 카페 '공부 가능' = 전량 적재 + UGC 태깅
공공데이터에 '공부 가능' 기준 자체가 없으므로 **선별 적재 불가**. 카페는 전량 적재하되:
- **STUDY_CAFE·도서관(열람좌석>0)**: 인제스천 시 `SourceSpec.defaultFeatures`로 `study_ok`/`quiet`를 `source=PUBLIC`·**낮은 confidence**로 자동 부여(set-based 백필, `UNIQUE(place_id, feature_type)` ON CONFLICT로 멱등).
- **일반 CAFE**: `study_ok`/`outlet`/`wifi`/`seating`/`no_eyes`를 **비운 채** 적재 → `reports`/`reviews` UGC로 채우고 confidence 상향. P5 동작구(상도·노량진) 필드테스트에서 카페 `study_ok` 제보 우선 시딩(콜드스타트).

### 4) 폐업 회전 대응 — soft-delete 선행
카페는 폐업 회전이 커, 대량 적재 **전에** `places.deleted_at`(또는 `active`) 마이그레이션을 선행하고, 스냅샷 `external_id` 집합에 없는 기존 행을 단일 set-based UPDATE로 비활성화(ADR-0002 멱등성 유지). → 이는 **로드맵 P3의 "스냅샷에서 사라진 행 soft-delete 비활성화" 항목**을 앞당겨 구현하는 것.

### 5) 정체성 프레이밍
카페는 "카페 추천앱"이 아니라 **"시원한 실내에서 오래 버티기(공부/작업)"의 survival 레이어**로 넣는다. survival 관점 feature(`study_ok`/`quiet`/`outlet`/`no_eyes`)로만 태깅하고, aspect 별점·리뷰 UI를 주인공으로 올리지 않는다(§9, ADR-0005 경계 유지).

## 검토한 대안(Alternatives)

| 대안 | 기각 이유 |
|---|---|
| LOCALDATA(휴게음식점)로 카페 확보 | 좌표 EPSG:5174 → 결측 시 카카오 지오코딩 물량 폭증(쿼터·비용). 상권정보는 WGS84라 지오코딩 0 → 상권정보 채택 |
| 카페를 '공부 가능'만 선별 적재 | 공공데이터에 '공부 가능' 기준이 없어 선별 불가 → 전량 적재 + UGC |
| 종류마다 category enum 신설(북카페·서점·독서실·라운지…) | 지도 필터/색상 복잡화. kind는 최소(CAFE/STUDY_CAFE), 나머지는 feature로 |
| 외부 혼잡도/좌석 API 의존 | §9 서사 우위(외부 API 없이 UGC 스코어링)와 충돌 |

## 결과(Consequences)

- `places` 볼륨: 화장실 6만 + 카페 9만 + 도서관 1.5만 = **15만+** → PostGIS 반경/kNN 성능·k6 부하테스트(P4)의 실전 소재.
- **블로커**: 대량 적재는 **공공데이터포털 오픈API serviceKey(무료)** 또는 다운로드한 CSV가 필요. serviceKey면 P3 무인화(EventBridge→ECS RunTask 주기 동기화 + soft-delete diff)까지 한 번에 연결됨.
- 착수 전 소규모 결정(enum·필터·명소 시드·soft-delete)은 WORKLOG에 why/대안 기록(CLAUDE.md §C).
- **미확정(실제 파일 필요)**: 각 CSV의 정확한 컬럼 헤더·charset, 상권정보 업종 소분류 코드값(2023 개편 — 하드코딩 금지, 업종코드 매핑표 15067631로 실측 확정).

## 착수 순서(Recommended Order) — 진행 상황(2026-07-09)

0. **골격**: `PlaceCategory` += CAFE/STUDY_CAFE. — ✅ **완료**(프론트 categories 동기는 미착수, 백엔드만 이번 범위).
1. 업종코드 매핑표(15067631)로 커피점·독서실/스터디카페 소분류 코드 실측 확정. — ❌ **미착수**(상권정보 API 승인 대기 중이라 코드값 대신 업종소분류명 텍스트 매칭으로 임시 구현, `StoreCategoryMapper`).
2. 스키마: `places` += `is_commercial`·`deleted_at`(V5). `IngestionService`/`PlaceBulkUpsertRepository` += set-based feature 백필 + 스냅샷 diff soft-delete(opt-in). — ✅ **완료**.
3. 전국도서관표준데이터(LIBRARY) 적재 코드 — ✅ **완료**(CSV가 아니라 JSON 오픈API로 경로 정정, 위 "구현 정정" 참고). **프로덕션 실적재는 미실행**(사용자 통제).
4. 상권정보 STUDY_CAFE → CAFE 적재 코드 — ✅ **코드 완료, ⚠️계약 미검증**(활용신청 미승인 403). 승인 후 재검증 필요.
5. 공공시설개방(CIVIC) 화이트리스트 필터 적재. — ❌ **미착수**(이번 범위 밖, 후속 확장).
6. 명소 시드(노들서가 등) 개별 등록 + 지오코딩. — ❌ **미착수**(이번 범위 밖, 후속 확장).
7. P3 무인화(serviceKey 오픈API 주기 동기화 + 여러 반경 호출을 합친 전국 soft-delete diff). — 🟡 **library 소스는 스캐폴드 완료**([ADR-0011](./0011-scheduled-public-data-sync.md), EventBridge Scheduler→ECS RunTask + `IngestBatchLock` 동시 실행 방지, 기본 DISABLED·미활성). 상권정보(STUDY_CAFE/CAFE)는 계약 미검증 상태라 여전히 스코프 밖.

## 근거(References)
- 리서치: 전국도서관표준데이터(15013109)·전국공공시설개방정보표준데이터(15013117)·소상공인 상가(상권)정보(15083033/15012005)·노들섬 노들서가(nodeul.org)·서울열린데이터(OA-15480/OA-21062)
- 카페 study_ok 크라우드소싱 벤치마크: 카공맵·카공지도(100% UGC)
- CLAUDE.md §3(전국 표준데이터 그대로 적재)·§9(간판 vs 살)·로드맵 P3(soft-delete)
