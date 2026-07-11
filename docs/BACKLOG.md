# 그늘(Geuneul) — 백로그

> **상태(2026-07-11): C1~C4(2차·심화 마무리) 전량 완료·머지·라이브. 계획+2차/심화 스코프 소진 — 남은 백로그 없음.**
> 로드맵 W0~P5 + 심화/additive(A1~A7·B1~B2) + F1~F5 + **N1~N9** + 실사용 피드백(#90) + 데스크톱 반응형(#92)·백엔드 하이진(#93)·오버레이 포커스(#94) + **C1~C4(#96~#99)** 전량 완료·머지·라이브. Flyway **V18**·ADR **0027**.
> C1~C4는 "버그·미완성"이 아니라 CLAUDE.md 규칙1/2로 의도적으로 미뤄뒀던 2차/심화였고, `/geuneul-finish`로 일괄 구동해 마감했다(아래 스펙은 이력·참고용). 새 백로그가 생기면 이 파일에 다시 정의한다.

> **철칙(CLAUDE.md·TS)**: 기능 브랜치 → PR → **머지 전 `gh pr checks <N>`로 "Backend (Gradle, JDK 21)" pass 눈확인(TS-025)** → squash 머지 → 배포·라이브 검증. 커밋 신원 `ghdtjdwn`/`seongjuice999@gmail.com`, **Co-Authored-By: Claude 금지(§A)**. 비밀·개인정보(키·IP)는 `.local`·env로만(§D). 결정은 웹 2026 트렌드 확인 + `WORKLOG.md`(무엇/왜/대안/근거), 사고는 `TROUBLESHOOTING.md`, 아키텍처는 `docs/adr/`. **로컬 IT는 colima docker-java 이슈로 skip → SKIP≠통과, CI가 유일 게이트(TS-009).** Boot 4=Jackson 3(TS-028), 외부 전송 비동기(TS-029), 시각컬럼=`Instant`+UTC(TS-016), 새 외부 API는 코드 前 실호출 계약검증(TS-026). **간판(지리·스코어링) 우선, 커뮤니티는 살(§9, 리뷰앱·친구망화 금지).** 침수/위험 표현 공포 조장 금지(§6). **범위 임의 확장 금지(§0-2).**
> **모델 역할 분리(규칙 E)**: 설계·검증·머지 판단은 세션 메인 모델(Opus/Fable). 단순·기계적 실작업은 **codex·agy 먼저(각 기본 코딩 모델)→토큰 소진 시 Sonnet 폴백**. 위임 결과는 메인이 검증 후 커밋.

---

## ✅ 완료: 2차·심화 마무리 (C1~C4) — `/geuneul-finish`로 일괄 구동 (2026-07-11)

> **전량 완료·머지·라이브.** C2(#96, a11y 트랩·콤보박스) → C1(#97, 신고/모더레이션 프론트) → C3(#98, 관심장소 알림·ADR-0026) → C4(#99, 그늘 경유 경로·ADR-0027). 각 항목 기능 브랜치→PR→`gh pr checks` Backend pass 눈확인(TS-025)→squash 머지→배포. **C1·C3·C4는 적대적 리뷰 워크플로로 확정 결함 선제 수정**(C1 중첩 다이얼로그 Esc·admin 페이저 / C3 재알림·타입유실·푸시누락·중복 / C4 0건). 아래 스펙은 이력·참고용.

### C1. 신고/모더레이션 프론트 진입점 【2차 · 규모 M】
- **무엇**: 로그인 사용자가 **최근 제보·후기 각 항목의 작은 "신고" 진입점**으로 사유(스팸/허위정보/불쾌·명예훼손/기타)+선택 코멘트를 골라 신고(POST /flags). **ADMIN 전용 검수 큐 화면**에서 PENDING 신고를 대상 요약과 함께 보고 RESOLVED(대상 숨김)/DISMISSED 처리. **§9대로 최소·비노출** — 하단 탭 신설 금지, 신고는 작은 텍스트/오버플로 버튼.
- **현황(근본)**: **백엔드 완비, 프론트 진입점 0건. 신규 마이그레이션 불필요.**
  - 백엔드: `V7__flags.sql`(테이블 존재). `FlagController`(POST /flags, 201, reporterId는 `@AuthenticationPrincipal`에서 — 바디로 안 받음), `AdminFlagController`(GET /admin/flags/pending, GET /admin/flags?status=, POST /admin/flags/{id}/resolve). enum: `FlagStatus`(PENDING/RESOLVED/DISMISSED), `FlagReason`(SPAM/FALSE_INFO/OFFENSIVE/OTHER), `FlagTargetType`(REPORT/REVIEW). `SecurityConfig`가 POST /flags=authenticated(401), /admin/**=hasRole("ADMIN")(403). resolve의 RESOLVED는 대상 콘텐츠 숨김. 중복 신고·이미 처리건 409, 대상 없음 404.
  - 프론트: `/api/flags`·`/api/admin` 프록시 없음, `types/flag.ts` 없음, `app/(shell)/admin` 라우트 없음, 후기/제보에 신고 배선 없음. 단 `types/user.ts` User에 `role:"USER"|"ADMIN"`이 이미 있고 `useMe()`가 내려줌(현재 미사용) → 관리자 UI 게이팅 즉시 가능. 신고 대상 id(Report.id·Review.id)도 렌더에서 이미 사용 중.
- **수정위치**:
  - `frontend/app/api/flags/route.ts`(신규 POST 프록시) · `frontend/app/api/admin/flags/route.ts`(신규 GET, **주의: `lib/backend.ts`의 proxyAuthed는 쿼리스트링을 포워딩 안 하므로 라우트에서 `request.nextUrl.searchParams`를 path에 직접 결합**) · `frontend/app/api/admin/flags/[id]/resolve/route.ts`(신규 POST)
  - `frontend/types/flag.ts`(신규, 백엔드 DTO와 1:1) · `frontend/lib/api.ts`(createFlag/fetchAdminFlags/resolveFlag, 기존 ApiError·CLIENT_TIMEOUT 패턴) · `frontend/lib/queries.ts`(선택: useAdminFlags/useResolveFlag)
  - `frontend/components/place/FlagButton.tsx`(신규 공용 — targetType/targetId prop, 사유 라디오+코멘트≤500 시트, 비로그인은 useMe로 분기해 토스트) · `ReviewsSection.tsx`·`PlaceDetailOverlay.tsx`(RecentReports 제보 li)에 최소 배치
  - `frontend/app/(shell)/admin/flags/page.tsx`(신규 검수 큐, useMe role 게이트) · `mypage/page.tsx`(role===ADMIN일 때만 조건부 링크, 하단 탭 신설 금지)
- **접근**: 신규 마이그레이션 없음. (1) types/flag.ts DTO 1:1 → (2) BFF 프록시 3개(proxyAuthedPost·proxyAuthed 재사용, admin GET은 searchParams 직접 결합) → (3) lib/api.ts 3함수 → (4) FlagButton(성공 "신고가 접수됐어요", 409 "이미 신고한 항목이에요", 비로그인 "로그인이 필요해요") → (5) 후기·제보 li 최소 배치 → (6) admin/flags 화면(GET status=PENDING→targetSummary→resolve→invalidate) → (7) mypage 조건부 링크.
- **수용기준**: ① 로그인 사용자 신고→201·토스트, 재신고→409, 비로그인→백엔드 안 가고 안내. ② ADMIN이 큐에서 PENDING 확인·RESOLVED(숨김)/DISMISSED, 비관리자/비로그인은 403/401 우아하게 처리(**현재 api.ts는 401만 특별처리 → 403도 처리 추가**). ③ §9대로 최소·비노출(신설 하단 탭 없음). ④ CI green. ⑤ Flyway 신규 0건.
- **주의**: §9 모더레이션이 주인공 되지 않게. §6 중립 톤. 클라 role 게이트는 UX용, 실제 방어는 백엔드. 신규 외부 API 없음(TS-026 비해당), 마이그레이션 없음(TS-016 비해당).

### C2. a11y 심화 — 오버레이 Tab 포커스 트랩 + 검색 콤보박스 화살표 nav 【심화 · 규모 M · 프론트 전용】
- **무엇**: 키보드 사용자 마감 2종. (1) 장소상세/작성자프로필 오버레이 열림 시 **Tab/Shift+Tab을 패널 안에서만 순환**(현재는 뒤의 지도·NavRail·TabBar로 샘). (2) 검색 드롭다운 **ArrowUp/Down 하이라이트→Enter 선택 + role=combobox/listbox/option·aria-activedescendant**. 마우스·터치·디바운스 동작 전부 유지.
- **현황**: #94에서 두 오버레이 role=dialog+열림 focus()/닫힘 복귀+Esc까지 완료. SearchBar는 Enter(첫 결과)/Esc만(#92). **없는 것**: (a) Tab 순환 가두기, (b) 검색 화살표 nav·콤보박스 ARIA. 감사가 "스코프 확장"으로 연기한 것(버그 아님).
- **수정위치**:
  - `frontend/lib/hooks.ts` — **공유 훅 `useDialogFocusTrap(panelRef, active, close, {trapTab})` 신설**(기존 두 오버레이의 Esc+focus-move+restore+신규 Tab트랩을 한 훅으로 DRY). 파일 상단 이미 'use client'.
  - `frontend/components/place/PlaceDetailOverlay.tsx`·`frontend/components/user/UserProfileOverlay.tsx` — 기존 두 useEffect를 훅 호출로 대체.
  - `frontend/components/map/SearchBar.tsx` — `useId` import, `active` 상태, onKeyDown에 ArrowUp/Down/Enter, input role/aria, ul role=listbox+id, 각 option(button)에 role=option+id+aria-selected+`tabIndex=-1`+하이라이트. `onChange`·디바운스 성공·`clear`·닫힘에서 `setActive(-1)`.
- **접근·핵심 판단**:
  - **트랩 범위 = `!(onMap && isLg)`** — 데스크톱 지도 탭('/')에선 오버레이가 400px 좌측 패널이고 옆 지도·NavRail이 살아있어(뒤가 inert 아님) 하드 트랩하면 키보드 사용자를 눈에 보이는 UI에서 격리 → **해로움**. 모바일 전 탭·데스크톱 비지도 탭은 오버레이가 전체를 덮으므로 트랩이 옳음. `isLg`=`matchMedia('(min-width:1024px)')`(+change 리스너 cleanup), `onMap`=usePathname()==='/'. Esc·focus-move·복귀는 두 레이아웃 다 유지.
  - Tab 트랩: keydown에서 포커서블 수집(`'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'` + `offsetParent!==null` 가시필터) → first↔last 래핑. 헤더 '뒤로' 버튼이 first 보장.
  - 콤보박스: `const listboxId=useId()`(두 SearchBar 동시 마운트라 정적 id 금지·useId 필수). option role은 **button에 두고 tabIndex=-1**(aria-activedescendant 패턴 — 옵션이 탭 스톱이면 화살표 nav와 충돌). **린트 검증됨**: 이 프로젝트 eslint에 no-interactive-element-to-noninteractive-role 미활성이라 `<button role=option>` 안전. 단 role-has-required-aria-props가 combobox의 aria-expanded 요구 → 반드시 포함.
- **수용기준**: 비지도/모바일에서 오버레이 Tab이 첫↔마지막 순환·뒤로 안 샘·Esc 복귀; 데스크톱 지도 탭은 의도적 비트랩(옆 지도로 이동 가능)+Esc 복귀. 검색 화살표로 하이라이트·activedescendant·Enter 선택, 입력변경/닫힘 시 초기화, 두 인스턴스 id 충돌 0. lint/typecheck/build green(CI 유일 게이트).
- **주의**: 마이그레이션·외부 API 없음. matchMedia cleanup 필수. 프론트 테스트 인프라 없음 → 수동 키보드 워크스루로 확인. (메모: `report/page.tsx` PlacePicker도 role=dialog라 훅 재사용 가능하나 이번 스코프 밖.)

### C3. 관심 장소 상태 변화 알림 — 북마크 장소 단건 유의미 제보(침수/미끄럼) 【심화 · 규모 M】
- **무엇**: 북마크(관심 장소, A7)한 장소에 **유의미한 안전 제보(FLOOD 침수·SLIPPERY 미끄럼)가 1건**이라도 올라오면 저장한 유저에게 인앱 알림 1건(+F2 푸시 기기엔 OS 배너). 문구는 §6 중립("최근 침수 제보가 있어요 · 우회를 권장해요"). **새 토글·새 화면 없음** — 기존 마이페이지 "관심 장소 소식" 토글·BOOKMARK_SURGE 규칙 그대로 재사용.
- **현황(근본)**: 파이프라인은 있고 **"단건 유의미 제보" 트리거만 없음.** 기존 BOOKMARK_SURGE는 `ReportSurgeService`의 **급증(≥3건/10분)일 때만** 발송(`handle()`이 surgeForPlace().ifPresent()로 게이팅) → 침수 **1건**은 임계 미달로 알림 안 감. 파이프라인: reports INSERT → V9 트리거 `pg_notify('geuneul_report_surge', place_id)` → `ReportNotificationListener.handle()` → `NotificationService.onSurge()` → `NotificationDeliveryRepository.insertBookmarkSurge()`(bookmarks 조인, dedup_key UNIQUE, 멀티인스턴스 1건, pushService 연동). ADR-0018 §2는 단건 상태변화를 범위 밖으로 남겨둠.
- **수정위치**(백엔드 중심):
  - `NotificationDeliveryRepository.java` — `insertBookmarkStatus(...)` 네이티브 INSERT(`insertBookmarkSurge` 복제, dedup_key 접두사 `bmstatus:`, bookmarks JOIN + notification_rules type='BOOKMARK_SURGE' 재사용, ON CONFLICT DO NOTHING).
  - `NotificationService.java` — `onBookmarkStatus(placeId)`: 최근 유의미 제보 타입 조회→없으면 return→§6 단수 중립 문구→insertBookmarkStatus + `pushService.sendToUser`(비동기·실패 격리).
  - `ReportNotificationListener.java` — `handle()`에서 onSurge와 **별개로 무조건** `onBookmarkStatus(placeId)` 호출(단건은 급증 게이트 밖). 예외 격리 유지.
  - `ReportRepository.java` — `findLatestMeaningfulReportType(placeId, types, since)`(미만료·미숨김 최신 1건, idx_reports_place_created 경로).
  - `SurgeInfo.java`(문구 톤 참조·'몰리고 있어요'→단수 '있어요') · `NotificationsSection.tsx`(L196 desc 소폭 조정, 선택) · 테스트 `NotificationServiceTest`·`NotificationFlowIT` · `docs/adr/000X-bookmark-status-change.md`(신규, 0018 확장).
- **접근**: **이벤트-드리븐(V9 LISTEN/NOTIFY 재사용) — 온디맨드 아님.** 근거: V9가 이미 모든 제보 INSERT마다 NOTIFY하므로 `handle()`에 한 줄 훅=신규 인프라 0. F5가 온디맨드로 간 이유(폭염은 상태·트리거 소스 없음)는 여기 적용 안 됨(제보는 이산 이벤트·트리거 존재). 침수 우회는 시급(§2 통학러)이라 실시간이 맞음. **신규 마이그레이션 불필요**: notification_deliveries.type VARCHAR(24)·dedup_key VARCHAR(200)이 `bmstatus` 키 수용, 규칙 재사용이라 컬럼 추가 0.
- **수용기준**: `NotificationFlowIT` — 유저A가 장소P 북마크+BOOKMARK_SURGE ON, P에 FLOOD 1건→onBookmarkStatus→GET /notifications 1건, body §6 중립('위험!' 없음). 같은 place·type 버킷 내 재호출 dedup 1건. 비유의미 타입(COOL·SEAT_OK) 단건은 0. 북마크 안 함·규칙 OFF는 0. CI Backend pass(로컬 IT skip→CI 게이트).
- **주의**: §9 — 새 규칙타입/토글/화면 만들지 말고 기존 BOOKMARK_SURGE에 병합. §6 단수 중립. 급증+침수 동시면 onSurge·onBookmarkStatus 둘 다 dedup_key 달라 2건 가능(정보 달라 허용, ADR 명시). 유의미 타입 FLOOD·SLIPPERY로 좁혀 스팸 방지. push 비동기(TS-029), 시각 Instant+UTC(TS-016). **ADR 필요**(0018 확장 — 단건 트리거·이벤트 재사용·유의미 타입 집합).

### C4. 그늘 경로 — 쿨링쉼터 경유지 경로 【심화 · 규모 M · 탐색적】
- **무엇**: 장소 상세 "화장실 경유 경로"(F3) 옆에 **"그늘 경유 경로"** 버튼. 현재위치→도착 사이 **우회 최소 쿨링쉼터/실내 1곳을 경유지로** 끼운 경로("가는 길에 쉼터에서 쉬어가기"). N8 shadeSpots 오버레이("길 근처 피할 곳")는 유지 — 이건 "실제로 쉼터를 통과하는 길". §3/§10 루트 3종(비/그늘/화장실) 중 '그늘 경로'를 F3와 대칭으로 채움.
- **현황**: F3 화장실 경로(GET /routes/toilet → `RouteService.toiletRoute` → `PlaceRepository.findBestToiletWaypoint`(corridor+detour-min, V3 GIST) → `KakaoDirectionsProvider`@Primary mode=road)·N8 shadeSpots 오버레이(`RouteService.SHADE_CATEGORIES`=COOLING_SHELTER/LIBRARY/UNDERGROUND, `findShadeAlongCorridor`)는 완료. 추천 시나리오 enum=REST30/RESTROOM/RAIN/FOCUS/LONGSTAY(**SHADE 없음**). **'그늘 경로'가 '경로'로는 없음** — N8은 peripheral 오버레이일 뿐. §0-2가 자체 가중 라우팅을 금지해 미뤄둠.
- **수정위치**:
  - `PlaceRepository.java` — `findBestToiletWaypoint`의 고정 category='TOILET'를 `:categories`(CSV `ANY(string_to_array)`, 인젝션 안전)로 일반화한 `findBestWaypointByCategories` 추가. `RouteWaypointView`에 `getCategory()`(p.category 프로젝션).
  - `RouteService.java` — `toiletRoute`를 공통 `viaRoute(from,to,categoriesCsv,라벨)`로 추출 → `toiletRoute`=TOILET 위임 + `shadeRoute`=SHADE_CATEGORIES 신설. 폴리라인·shadeSpots 오버레이 공통.
  - `RouteController.java`(GET /routes/shade, /routes/toilet 미러·공개·requireKorea) · `RouteResponse.java`/`RouteWaypointView.java`(waypoint category 노출 — 미니맵 아이콘 구분용).
  - `frontend/lib/api.ts`(fetchRoute(via) 일반화) · `frontend/app/api/routes/shade/route.ts`(신규 1줄 미러) · `PlaceDetailOverlay.tsx`(두 번째 버튼 onShadeRoute, setRoute 재사용) · `RouteMiniMapLive.tsx`(경유지 마커 하드코딩 'toilet'→waypoint.category 아이콘) · `types/route.ts`(waypoint category) · `docs/adr/000X-shade-waypoint-route.md`.
- **접근(제안 수준·방어적 최소안)**: (1) 리포 일반화 → (2) 서비스 대칭화(F3 인프라 100% 재사용, 경유지 없으면 기존 graceful 폴백) → (3) GET /routes/shade → (4) DTO waypoint category → (5) 프론트 버튼·프록시·미니맵 아이콘 → (6) ADR·테스트 미러. **새 마이그레이션 불필요**(V18 유지, V3 GIST 재사용), **새 외부 API 없음**(Kakao directions 재사용, TS-026 이미 충족).
- **핵심 방어선(§0-2)**: **자체 가중 라우팅 금지.** "우회최소 경유지 1곳"을 PostGIS corridor 쿼리(간판)로 고르고 폴리라인은 기존 DirectionsProvider가 그린다 — **F3와 완전 대칭**. 프롬프트에서 나온 "그늘 많은 경로 재랭크 토글"안은 대안경로 다중 추출+Kakao 대안경로 카운팅(라우팅 creep·TS-026 재검증)이라 **범위 제외**.
- **수용기준**: 상세에서 "그늘 경유 경로"→미니맵에 출발→쿨링쉼터(경유지 마커=쉼터 아이콘)→도착 폴리라인+배지 "그늘 경유(+피할 곳 N)". 경유 쉼터 없으면 직선/도로+오버레이만 폴백. GET /routes/shade가 /routes/toilet과 동일 스키마. CI Backend pass(RouteServiceTest/RouteToiletIT shade 케이스). 마이그레이션 무변경.
- **주의**: 가장 탐색적 → 범위를 "경유지 1곳"에 고정. 대안 최소안(규모 S): RecommendationScenario에 SHADE 추가(순수 additive)지만 REST30/LONGSTAY와 겹치고 '경로'가 아니라 §3 라인 못 채움 → 경유지 경로안이 우위, 시간 압박 시에만 폴백. N8 오버레이와 중복 인상은 UX 카피로 분리. DTO waypoint category는 additive지만 프론트 타입·미니맵 동시 수정 필요(안 하면 쉼터가 화장실 아이콘).

> **ADR 번호**: C3·C4 각각 신규 ADR(현재 최신 0025) — 머지 순서로 0026·0027 확정. C1·C2는 ADR 불필요(C1 기존 백엔드 계약, C2 프론트 폴리시).

---

## ✅ 완료 (참고용)

| 사이클 | PR | 산출물 |
|---|---|---|
| 로드맵 W0~P4 + A1~A7·B1~B2 | #61~#69 | V13·V14·V15, ADR 0017~0019 |
| F5 폭염 피난 알림 | #73 | HEAT_ESCAPE 온디맨드·§6 권유형 (ADR-0020) |
| F1 상권 9도시 · F3 화장실 도로 폴리라인 | 스크립트·#76 | KakaoDirectionsProvider@Primary·기존 키 재사용 (ADR-0021) |
| F2 Web Push | #77·#79 | zerodep·async·라이브 (ADR-0022) |
| **N1~N9** | #81~#88(+브랜딩 #86) | 사진 presigned-GET·댓글UX·하단시트 3단·지정검색·내글관리·팔로우(V17·ADR-0023)·그늘/비 corridor(ADR-0024)·대규모대비(V18·ADR-0025) |
| 실사용 피드백 | #90·#91 | 푸시 204 프록시 오탐(TS-032)·하단시트 peek 46px |
| **데스크톱 반응형** | #92 | ≥lg 3분할(NavRail·MapSidebar·PlaceListBody)+감사 20건(오버레이 라우트 스코핑·hover/cursor/focus/Esc·문서 정합성) |
| 백엔드 하이진 · 오버레이 포커스 | #93·#94 | orphan @Component 제거·경로 javadoc · role=dialog+focus in/restore |
| **C1~C4 2차·심화 마무리** | #96~#99 | C2 a11y 트랩·콤보박스 · C1 신고/모더레이션 프론트 · C3 관심장소 알림(ADR-0026) · C4 그늘 경유 경로(ADR-0027) |

**결정 로그**:
- **F1 잔여 중소도시 = 미실시(WON'T DO)** — 9도시로 충분, data-padding 회피(2026-07-11).
- **작성자 팔로우 = "커먼스 세이프"만** — 팔로워 수만 공개·목록 없음·피드 없음(§9, N7·ADR-0023).
- **데스크톱 = 지도앱 표준 3분할** — 카카오/네이버맵 정렬, 모바일 무변경(≥lg, #92).
- **C4 그늘 경로 = 경유지 1곳(F3 대칭)** — 자체 가중 라우팅·대안경로 재랭크는 §0-2로 범위 제외. (완료 #99, ADR-0027: `findBestWaypointByCategories` 일반화·GET /routes/shade.)
- **C3 알림 = 이벤트-드리븐(V9 재사용)·기존 규칙 병합** — 새 규칙타입/토글 금지(§9). (완료 #98, ADR-0026: 제보시각 dedup 버킷·DISTINCT ON 타입별·RETURNING 정확히 1회 푸시.)
- **C3 push 정확성 = RETURNING** — 사전 SELECT+inserted>0 가드는 per-user dedup_key라 누락·중복(적대적 리뷰 확정) → `INSERT ... RETURNING user_id`(EntityManager 커스텀 조각)로 실제 삽입 유저만 push.
- **C1 신고 프론트 = §9 최소** — 하단 탭 신설 없이 작은 텍스트 버튼 + mypage 조건부 admin 링크(완료 #97). 중첩 다이얼로그 Esc 이중닫힘은 `useDialogFocusTrap`이 nested aria-modal에 양보하도록 수정.
- **C2 트랩 범위 = `!(onMap && isLg)`** — 데스크톱 지도 탭은 옆 지도가 살아있어 비트랩(완료 #96).

## 남은 사용자 액션
- **없음.** C1~C4 전량 완료·머지·라이브. 남은 백로그 없음(외부 승인·키 블로커 0건). 새 요구가 생기면 이 파일에 백로그를 다시 정의한다.
