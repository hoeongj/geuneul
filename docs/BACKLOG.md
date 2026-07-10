# 그늘(Geuneul) — 백로그 (다음 세션 실행 대상)

> **상태(2026-07-10)**: 심화+additive 코드(A1~A7·B1~B2)는 **전량 구현·머지·라이브**(PR #61~#69). 사용자 승인 데이터 op(A3 쉼터 냉방 재적재·A8 6대 광역시)도 실행 완료. **이 문서는 이제 "후속·외부 대기·신규 심화" 백로그**다. `/geuneul-start`가 이 문서를 로드해 구동한다.
>
> **철칙(CLAUDE.md·TS)**: 기능 브랜치 → PR → **머지 전 `gh pr checks <N>`로 "Backend (Gradle, JDK 21)" pass 눈확인(TS-025)** → squash 머지. 커밋 신원 `ghdtjdwn`/`seongjuice999@gmail.com`, **Co-Authored-By: Claude 금지(§A)**. 비밀·개인정보(키·IP)는 `.local`·env로만(§D). 결정은 웹 2026 트렌드 확인 + `WORKLOG.md`(무엇/왜/대안), 사고는 `TROUBLESHOOTING.md`, 아키텍처는 `docs/adr/`. 새 외부 API는 코드 前 실호출 계약검증(TS-026). 네이티브 프로젝션 시각컬럼=`Instant`+UTC(TS-016). 로컬 IT는 colima skip → **SKIP≠통과, CI가 유일 게이트(TS-009)**. 간판(지리·스코어링) 우선, 커뮤니티는 살(§0-9). 침수/위험 표현 공포 조장 금지(§6).

---

## ✅ 완료 (2026-07-10 심화+additive 세션) — 참고용

| 항목 | PR | 산출물 |
|---|---|---|
| A1 시설 comfort SQL 통합 | #61 | V13 `place_feature_signals` 뷰 + SurvivalScore 단조 상승 (ADR-0017) |
| A2 verified→trust 보너스 | #62 | TrustScore verified 보너스(캡 20)·`countByUserIdAndVerifiedTrue` |
| A3 쉼터 냉방 조건부 백필 | #63 | CSV 파이프라인 ConditionalFeature **+ 실 재적재(57,070 백필, 라이브)** |
| A4 급증 SSE 프론트 | #64 | `proxyStream`·EventSource·SurgeBanner |
| A5 popular-times 히트맵 | #65 | 요일선택+24h 스트립(dataviz 발산 팔레트 검증) |
| A6 후기 커뮤니티 최소 UI | #66 | 댓글·유용해요 토글(§0-9 최소) |
| A7 bookmarks | #67 | V14 `domain.bookmark` + ★버튼·마이페이지 |
| A8 상권 전국 확장 | 스크립트 | **6대 광역시**(부산·대구·인천·대전·광주·울산) ≈37,618행 |
| B1 알림 | #68 | V15 `domain.notification` — 급증 재사용+인앱 센터 (ADR-0018) |
| B2 루트 | #69 | `domain.route` — detour 최소 경유지+직선 MVP (ADR-0019) |

라이브(태스크데프 **rev56**): API·App 200 · `/me/bookmarks`·`/notifications` 401 · `/routes/toilet` 200. Flyway **V13·V14·V15** 적용. 상세 근거는 `WORKLOG.md`(2026-07-10 A1~B2·데이터 op 항목) + ADR 0017~0019.

---

## 🎯 다음 세션 후속 백로그 (F1~F6)

> 실행 순서 권장: **쿼터만 있으면 되는 것(F1)** → **사용자 액션이 필요한 것(F2·F3)** → **트래픽/설계 대기(F4·F6)**. 각 항목 = 코드 있는 것은 1 PR, 데이터 op는 스크립트. **F5는 완료(ADR-0020).**

### F1. A8 상권 나머지 지역 확장 【데이터·간판 · 사용자 쿼터만】 ← 진행중(9개 도시 완료 2026-07-10)
- **진행**: 서울 + 6대 광역시 + **9개 도시(수원·성남·용인·창원·청주·전주·천안·포항·김해·제주) 완료**(2026-07-10, ≈24,204행, 쿼터 에러 0, WORKLOG 참조). 남은 중소도시는 후속(멱등, 하루 단위).
- **무엇**: 나머지 중소도시(안산·안양·부천·평택·아산·구미·진주·목포·여수·원주·춘천 등)로 확대(멱등).
- **어떻게**: `./infra/scripts/prod-ingest-stores.sh <minLng,minLat,maxLng,maxLat> 1500` — **반드시 한 번에 하나씩(순차)**. ⚠️ **`IngestBatchLock`이 동시 실행을 막는다** — 병렬로 띄우면 뒤 태스크가 skip(exitCode 0이나 미적재, WORKLOG 2026-07-10 A8 함정). data.go.kr **일일 쿼터**라 하루 단위로 나눠. 완료 판정은 로그 `[store-api] ingestArea 완료`(스크립트 `aws ecs wait` 10분 캡 주의).
- **수용 기준**: 대상 도시 `/places?category=CAFE` 200. resultCode 쿼터 에러 나오면 다음 날.

### ~~F2. B1 Web Push 전송 배선~~ ✅ 코드완료 (2026-07-11, PR #77, ADR-0022) — 실기기 최종확인만 남음
- **한 것**: `domain.push`(V16 push_subscriptions·PushService·PushController `/push/subscribe|test|public-key`·WebPushConfig)·HEAT_ESCAPE에 push additive·프론트 Serwist SW `push`/`notificationclick`+구독 UI. **라이브러리 = `zerodep-web-push-java`**(JDK 내장 crypto, BouncyCastle 없음 → ADR-0018 미룬 사유 해소). `push.enabled` 플래그(기본 off=회귀 0). VAPID 키 SSM/env로 활성화. 단위테스트 6건.
- **함정**: Boot 4=Jackson 3라 Jackson2 `ObjectMapper` 주입 시 컨텍스트 부팅 실패(TS-028, CI에서만 드러남) → 직접 직렬화로 해소.
- **남은 것**: 활성화 배포 후 **실기기(설치형 PWA)에서 `/push/test`로 OS 배너 1회 최종 확인**(iOS는 Safari 설치 PWA 필수).

### ~~F3. B2 카카오모빌리티 도로 폴리라인~~ ✅ 완료·라이브 (2026-07-11, PR #76, ADR-0021)
- **한 것**: `KakaoDirectionsProvider`(@Primary) — `POST apis-navi.kakaomobility.com/v1/waypoints/directions`. **핵심: 새 키 발급 불필요** — 기존 카카오 REST 키(지오코딩/로그인용, 이미 SSM 배선)가 navi 엔드포인트에 인가됨을 실호출 계약검증(TS-026). 프론트 `RouteMiniMap` 폴리라인 오버레이(road 실선/straight 점선). **라이브 검증: `/routes/toilet` mode=road, 113정점, 실 경유 화장실.** 키 없으면 직선 폴백(graceful).

### F4. B2 그늘/비 경로 【심화 · 설계】
- **무엇**: ADR-0019 스코프만 기록. **단순화**: 경로 주변 그늘/실내 POI(쉼터·도서관·지하상가) 오버레이 표시(자체 가중 라우팅은 §0-2 지양). F3 이후.

### ~~F5. HEAT_ESCAPE 알림 평가~~ ✅ 완료 (2026-07-10, ADR-0020)
- **한 것**: **온디맨드(pull) 평가** — 알림 센터(`GET /notifications`)를 열 때 활성 HEAT_ESCAPE 규칙을 평가(새 스케줄러 없음, §0-2). `HeatComfort.isHeatAdvisory`(체감 ≥33℃ 폭염주의보) + kNN `findNearest(COOLING_SHELTER,1)` + 3시간 버킷 dedup으로 §6 권유형 1건 발송. 프론트 "폭염 피난 추천" 토글(현재 위치=중심). 단위테스트 5건 추가.
- **왜 주기 스케줄이 아니라 온디맨드인가**: Web Push(F2) 미배선이라 발송은 앱을 열 때만 보임 → 주기생성해도 유저가 보는 결과 동일한데 인프라만 늘어남. F2 배선 시 트리거를 주기평가로 승격(로직 재사용). 상세: ADR-0020, WORKLOG 2026-07-10 F5.

### F6. A9 Fargate task_cpu 512 【인프라 · 트래픽 대기】
- **무엇**: 부팅 93초 단축(TS-005 근본). **트래픽·부하 붙을 때만**(§0-2). k6 재부하 결과로 판단. 지금 대기.

---

## 신규 심화 아이디어 (선택 — 제안 후 착수)
- **P5 실사용**: 동작구(상도·노량진) UGC 필드테스트·시딩(콜드스타트, 배포는 라이브).
- **관측성 심화**: Grafana 대시보드·알림 규칙, k6 재부하 + EXPLAIN 재튜닝(신규 뷰 V13 포함).
- **A1 후속**: 부정 시설(소음) 별도 risk 채널(현재는 comfort 상쇄만, ADR-0017 한계).

---

## 완료 체크리스트 (2026-07-10 세션)
- [x] A1 comfort SQL 통합 (V13, ADR-0017) — #61
- [x] A2 verified→trust — #62
- [x] A3 쉼터 air_conditioned 백필 (코드 #63 + 실 재적재 57,070)
- [x] A4 급증 SSE 프론트 — #64
- [x] A5 popular-times 히트맵 — #65
- [x] A6 커뮤니티 최소 UI — #66
- [x] A7 bookmarks (V14) — #67
- [x] A8 상권 전국 확장 (6대 광역시)
- [x] B1 알림 (V15, ADR-0018) — #68
- [x] B2 루트 (ADR-0019) — #69
- [ ] A9 Fargate cpu 512 → **F6** (트래픽 대기)

> 다음 세션: 위 **F1~F6 중 준비된 것부터**(F1은 쿼터만, 바로 가능). 완료 시 WORKLOG·ADR·HANDOFF 갱신.
