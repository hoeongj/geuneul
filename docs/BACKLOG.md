# 그늘(Geuneul) — 다음 세션 실행 백로그 (심화 + additive 전량)

> **사용자 지시(2026-07-10)**: "심화나 있으면 좋은 것들 **전부 다 하나도 빠짐없이** 다 하라." → 다음 세션은 이 문서의 **A(additive)부터 B(심화)까지 전 항목**을 순서대로 착수·완료한다. 이 문서는 `HANDOFF.md`(현황)와 짝이며, `/geuneul-start` 명령이 이 문서를 로드해 실행한다.
>
> **현재 상태 요약**: 로드맵 W0~P4 + 데이터 커버리지(~146,000곳) 완성·라이브, **외부 승인 블로커 0건**(상권 #56·쉼터 #58 해소). 남은 것 = 이 문서의 심화/additive뿐.
>
> **철칙(CLAUDE.md·TS)**: 기능 브랜치 → PR → **머지 전 `gh pr checks <N>`로 "Backend (Gradle, JDK 21)" pass 눈으로 확인(TS-025)** → 머지. 커밋 신원 `hoengj`/`seongjuice999@gmail.com`, **Co-Authored-By: Claude 트레일러 금지(§A)**. 비밀·개인정보(IP 등)는 `.local`·env로만(§D). 결정은 웹 2026 트렌드 확인 + `WORKLOG.md`(무엇/왜/대안), 사고는 `TROUBLESHOOTING.md`. 네이티브 프로젝션 시각컬럼=`Instant`+UTV 부착(TS-016). 로컬 IT는 colima로 skip될 수 있음 → **SKIP≠통과, CI가 유일 게이트(TS-009)**. 간판(지리·스코어링) 우선, 커뮤니티는 살(§0-9). 침수/위험 표현은 공포 조장 금지(§6).

---

## 실행 순서 (권장)
작고 확실한 것(A)부터 momentum을 쌓고, 대형 심화(B)는 ADR 선행 후 착수. 각 항목 = 원칙적으로 1 PR.

**A. Additive 폴리시(간판 마무리·백엔드 준비분 프론트 노출)** → **B. 심화 tier(알림·루트, ADR 선행)**

> ⚠️ **중간에 사용자 입력이 필요할 수 있는 지점**(미리 알림): B2 루트의 외부 경로 API(카카오모빌리티 등) 활용신청/키, B1 알림의 Web Push VAPID 키(직접 생성 가능), A3/A8의 대량 재적재(safetydata IP·API 호출량). 해당 지점에서 한 줄 제안 후 진행.

---

# A. Additive 폴리시

## A1. 시설 comfort → survival_score SQL 통합 【간판】
- **무엇**: 현재 `place_features` 등급은 표시(`FeatureGrade`)만 하고 **survival_score엔 안 들어간다**. air_conditioned·outlet·wifi·seating·water(POSITIVE)와 noise 등(NEGATIVE)을 `comfort_score` 성분에 반영.
- **어디서**: `place_report_signals` 뷰(V4→V6→V10→V12 계보) 또는 스코어드 쿼리(`PlaceRepository`의 `comfortScore`). 신규 마이그레이션 **V13**에서 `place_features`를 comfort에 조인(polarity·confidence 가중). 순수함수 `SurvivalScore`는 comfort_score를 받으므로 뷰/쿼리만 손보면 됨.
- **왜**: 냉방 쉼터·콘센트 카페가 **무제보라도** comfort↑ → 마커색·종합점수에 반영(간판 정밀화). ADR-0007 스코어 계보 준수.
- **수용 기준**: 냉방 쉼터가 comfort 성분↑로 등급 상승, 마커 3색 흔들림 최소(§9). 실 PostGIS IT로 뷰 시맨틱 확증.
- **함정**: 재정규화(가중치 합), confidence 낮은 PUBLIC feature가 UGC를 덮지 않게. ADR로 근거 기록.

## A2. verified 방문인증 → 유저 trust_score 연동 【간판】
- **무엇**: 현재 `reports.verified`(V10)는 **제보 단위 뷰 가중(×1.3)** 만. 방문인증 제보를 꾸준히 한 유저의 `trust_score`를 올려 선순환.
- **어디서**: `domain.auth` trust(V6 trust weight 계보), `ReportRepository`. trust 재계산에 verified 제보 수 반영.
- **왜**: 허위제보 억제(§0-7). 신뢰 유저 제보가 스코어에 더 실림.
- **함정**: 재계산 트리거(제보 시/배치). 어뷰징(자기 verified 남발) 상한.

## A3. 쉼터 air_conditioned 조건부 백필 【간판·데이터】
- **무엇**: 쉼터는 CSV 스냅샷 경로로 적재돼 **air_conditioned feature가 없다**(그 백필은 API 서비스 `ShelterIngestionService` 전용인데 IP 제한으로 prod 미사용). 냉방기 보유(COLR_HOLD_ARCNDTN>0) 쉼터에 air_conditioned(낮은 confidence) 부여.
- **접근(택1)**: (a) 로컬(등록 IP)에서 스냅샷 재생성 시 CSV에 `COLR_HOLD_ARCNDTN` 컬럼 포함 → `SourceSpec`/`StandardCsvParser`에 **조건부 feature 백필** 지원 추가(또는 `SourceSpec.defaultFeatures` 확장) → CSV 재적재. (b) safetydata **IP 해제 후** `prod-ingest-shelter.sh`(API 서비스, 이미 백필 로직 보유)로 적재.
- **연계**: A1(comfort SQL 통합)과 합치면 냉방쉼터 comfort↑가 실동작.
- **함정**: 조건부(냉방기>0만). 재적재는 `.local/safetydata.env` 등록 IP(로컬 세션)에서 다운로드.

## A4. 급증 알림 프론트 구독 + 지도 급증 배지 【간판·프론트】
- **무엇**: 백엔드 `GET /alerts/stream`(SSE)·`GET /alerts/surge?bounds=`(스냅샷) 라이브(#44). 프론트에서 **EventSource 구독** → 지도에 "제보 급증" 배지/토스트, 리스트 강조.
- **어디서**: `frontend/app/(shell)/page.tsx`(홈 지도), `lib`, BFF(`app/api`). SSE는 BFF로 프록시(ADR-0004, text/event-stream 패스스루).
- **왜**: 실시간 UGC 시공간(간판)의 사용자 체감 완성.
- **함정**: SSE 재연결/백오프, BFF의 스트리밍 프록시(청크·keep-alive), 표현 순화(§6).

## A5. popular-times 히트맵 UI 【살·프론트】
- **무엇**: 백엔드 `GET /places/{id}/popular-times`(요일×시간 집계, Redis 1h 캐시) 라이브(#45·#53). 상세 화면에 히트맵/시간대 막대.
- **어디서**: `frontend/components/place/PlaceDetailOverlay.tsx`. **`dataviz` 스킬로 차트 규격** 준수(색·접근성·다크모드).
- **함정**: 데이터 희소(제보 적은 장소)일 때 폴백 표시. 간판 안 가리게.

## A6. 후기 커뮤니티 최소 UI (댓글·리액션) 【살·프론트】
- **무엇**: 백엔드 `POST/GET /reviews/{id}/comments`·`POST/DELETE /reactions`(#49) 라이브. 상세 후기에 **최소** 댓글 목록/작성 + "유용했어요" 토글.
- **왜/규율**: §0-9 — 커뮤니티가 **전면에 나오지 않게**(간판 우선). 리뷰앱화 금지.
- **함정**: 로그인 게이팅, 낙관적 UI, 리액션 멱등(카운트).

## A7. 관심 장소(bookmarks) — 테이블·API·UI 【살, B1 선행 의존성】
- **무엇**: ERD `bookmarks(user_id, place_id, memo, created_at)` **미구현**. 저장/해제 + 마이페이지 목록. **B1 알림의 "관심 장소 상태 변화"의 선행**이라 A에서 먼저.
- **어디서**: 신규 `domain.bookmark`, Flyway **V14**(V13은 A1이 선점 가능 — 번호 충돌 주의), API `POST /bookmarks`·`DELETE /bookmarks/{placeId}`·`GET /me/bookmarks`. 프론트 상세 "저장" 버튼 + 마이페이지 탭.
- **함정**: 유니크(user_id, place_id), 로그인 필요.

## A8. 상권 카페/스터디카페 전국 확장 【데이터·간판】
- **무엇**: 현재 **서울만**(distinct 29,886). `prod-ingest-stores.sh`에 더 큰 bbox로 광역시→전국 확대(멱등).
- **어디서**: `infra/scripts/prod-ingest-stores.sh <minLng,minLat,maxLng,maxLat> [radius]`. 6대 광역시부터 단계적.
- **함정**: data.go.kr **일일 호출 한도**(격자 셀×2코드) — 하루 단위로 나눠 실행. `aws ecs wait` 10분 캡(대형 격자는 로그/exitCode로 판정, WORKLOG 참고).

## A9. Fargate task_cpu 512 (조건부) 【인프라】
- **무엇**: 부팅 93초 단축(TS-005 근본 해결). **트래픽·부하가 붙을 때만**. 지금은 대기.
- **어디서**: `infra/terraform`(task def cpu). k6 부하 결과로 판단.

---

# B. 심화 tier (신규 대형 — ADR 선행 필수)

## B1. 알림 (Notifications) 【심화, CLAUDE.md §3·§9】
- **무엇**: ① 내 주변 침수/벌레 **급증 알림**, ② **관심 장소 상태 변화**(A7 bookmarks 의존), ③ **폭염 피난 추천**(날씨 트리거). ERD `notifications(id, user_id, type, condition_json, is_active, created_at)` — **미구현**.
- **선행 ADR**: 전달 방식 결정 — **PWA Web Push(VAPID)** vs **인앱 알림 센터(+기존 SSE)**. 권장: 인앱 센터 + SSE를 MVP로, Web Push는 stretch(VAPID 키는 `web-push`로 직접 생성 가능 → `.local`/SSM). 2026 트렌드(Web Push on iOS PWA 지원) 웹검색 확인 후 결정, WORKLOG 기록.
- **재사용**: `domain.alert`(LISTEN/NOTIFY→SSE, `ReportNotificationListener`·`SurgeEmitterRegistry`) — 급증 신호 인프라 그대로. `domain.weather`(폭염 트리거). `bookmarks`(A7).
- **구현 조각**:
  1. Flyway: `notifications`(규칙) + 필요시 `notification_deliveries`(발송 이력·dedup).
  2. `domain.notification`: 규칙 CRUD `POST /notifications/rules`·`GET /notifications/rules`·`PATCH .../{id}`(활성 토글), 평가기(제보 급증 이벤트/스케줄에서 규칙 매칭 → 발송).
  3. 전달: 인앱(`GET /notifications` 폴링 또는 SSE 확장) → (stretch) Web Push(service worker `push` 핸들러 + VAPID).
  4. 프론트: 알림 설정 UI(반경·타입) + 알림 센터 + (Web Push 권한 요청).
- **규율/함정**: **§6 공포 조장 금지**("위험!" 금지 → "최근 침수 제보, 우회 권장"). **dedup/rate**(같은 급증을 반복 알림 금지 — cooldown). 위치 프라이버시(규칙의 반경 중심좌표 저장 최소화). Web Push 키는 비밀(§D).
- **수용 기준**: 규칙 생성 → 조건 충족 시 1회 알림(중복 없이) → 인앱 표시. IT.

## B2. 루트 (Routes) 【심화, 가장 큼·리스크】
- **무엇**: ① **화장실 포함 경로**(A→B 중간에 화장실 경유), ② **그늘 경로**, ③ **비 피하는 경로**.
- **선행 ADR + 외부 결정**: 자체 라우팅(pgRouting=도로망 데이터 대공사)은 과설계 → **외부 경로 API + 우리 POI 오버레이**가 현실적. 후보: **카카오모빌리티 길찾기 API**(활용신청·키 필요 → 사용자 액션 가능) 또는 OSRM 자가호스팅. 웹검색으로 2026 국내 길찾기 API 옵션·쿼터·요금 확인 후 ADR.
- **현실적 스코프(단계)**:
  1. **화장실 포함 경로(MVP)**: A→B에서 `/places/nearest?type=TOILET` 경유지 1개 삽입 → 외부 directions API로 waypoint 경로. **먼저 구현**.
  2. **그늘/비 경로(심화)**: 경로상 그늘 POI 밀도·강수 가중은 난도 높음 → **단순화**(경로 주변 그늘/실내 POI 표시) 또는 설계만 기록하고 후속. 무리한 자체 가중 라우팅은 §0-2(과설계 금지)로 지양.
- **함정**: 외부 API 키(사용자 활용신청 필요 시 **한 줄 제안 후 진행**), 쿼터·요금, 경로 좌표계, 표현 순화(§6).
- **수용 기준**: 화장실 포함 경로 1개 시나리오 end-to-end(출발·도착 → 경유 화장실 → 경로 폴리라인). 그늘/비는 ADR에 스코프 명시.

---

# 조건부/의존 항목 (사용자·외부 상태 대기)
- **safetydata 자동 동기화**: 사용자가 safetydata 키 **IP 제한 해제**(allow-all) 시 → `domain.ingest.safetydata`(구현·테스트 완료) + `prod-ingest-shelter.sh`로 ECS 직접 API + EventBridge 주기 동기화 배선. (HANDOFF ⏳/README 참고. IP 안 풀리면 로컬 스냅샷 재적재로 유지.)
- **B2 외부 경로 API 키**: 카카오모빌리티 등 활용신청 필요 시 사용자 액션.
- **B1 Web Push VAPID 키**: `web-push` CLI로 생성 가능(사용자 승인 후 `.local`/SSM).

---

# 완료 체크리스트 (다음 세션이 갱신)
- [x] A1 comfort SQL 통합 (V13) — 뷰 place_feature_signals + SurvivalScore 단조 상승(ADR-0017). 머지 완료(#61).
- [x] A2 verified→trust — TrustScore verified 보너스(캡 20), countByUserIdAndVerifiedTrue. 머지 완료(#62).
- [x] A3 쉼터 air_conditioned 백필 — CSV 파이프라인 조건부 백필 코드(냉방기>0). 실 재적재는 대량 재적재(사용자 입력 지점). 머지 완료(#63).
- [x] A4 급증 SSE 프론트 구독 — EventSource + 스냅샷 폴링, SurgeBanner(§6 중립). 머지 완료(#64).
- [x] A5 popular-times 히트맵 UI — 요일선택+24h 스트립(발산 팔레트 검증). 프론트 CI green.
- [x] A6 커뮤니티 최소 UI — 후기 댓글(지연 로드)+유용해요 토글, 최소 표면(§0-9). 프론트 CI green.
- [x] A7 bookmarks (테이블·API·UI) — V14 + domain.bookmark + BookmarkButton/마이페이지. CI pass 후 머지.
- [ ] A8 상권 전국 확장
- [ ] A9 Fargate cpu 512 (조건부)
- [x] B1 알림 (ADR + 구현) — V15 + domain.notification + 급증 재사용 평가 + 인앱 센터(ADR-0018). CI pass 후 머지.
- [ ] B2 루트 (ADR + 화장실 경로 MVP)

> 각 완료 시: WORKLOG(무엇/왜/대안) + 필요시 ADR + TROUBLESHOOTING(사고) 기록, 이 체크박스와 HANDOFF 갱신.
