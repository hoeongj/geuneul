# 그늘(Geuneul) — 백로그

> **상태(2026-07-11): 기능 완료·동결 + 자산화 사이클 D1~D5 전량 완료·머지·라이브(#102~#106).** 포트폴리오 마무리(README 30초 케이스 스터디·아키텍처 다이어그램·면접 STAR)와 무료 출시(/install WebAPK+iOS 홈화면)까지 끝. **남은 백로그 없음.**
> 로드맵 W0~P5 + 심화/additive(A1~A7·B1~B2) + F1~F5 + **N1~N9** + 실사용 피드백(#90) + 데스크톱 반응형(#92) + **C1~C4(#96~#99)** 전량 완료·머지·라이브. Flyway **V18**·ADR **0027**.
> **결정(2026-07-11, 사용자와 합의)**: 기능은 여기서 **동결(feature freeze)** — 간판(PostGIS 지리·UGC 스코어링)·심화(k6·동시성·관측성)·완결성 모두 충분, 더 넣으면 한계효용 체감 + §9 리스크(커뮤니티 주인공화). 남은 진짜 작업은 **채용용 자산화 + 무료 배포**다. 아래 D1~D5는 그 실행 백로그(CLAUDE.md 규칙B대로 2026 웹검증 반영). 아래 C1~C4 스펙은 이력·참고용.

> **철칙(CLAUDE.md·TS)**: 기능 브랜치 → PR → **머지 전 `gh pr checks <N>`로 "Backend (Gradle, JDK 21)" pass 눈확인(TS-025)** → squash 머지 → 배포·라이브 검증. 커밋 신원 `hoengj`/`seongjuice999@gmail.com`, **Co-Authored-By: Claude 금지(§A)**. 비밀·개인정보(키·IP)는 `.local`·env로만(§D). 결정은 웹 2026 트렌드 확인 + `WORKLOG.md`(무엇/왜/대안/근거), 사고는 `TROUBLESHOOTING.md`, 아키텍처는 `docs/adr/`. **로컬 IT는 colima docker-java 이슈로 skip → SKIP≠통과, CI가 유일 게이트(TS-009).** Boot 4=Jackson 3(TS-028), 외부 전송 비동기(TS-029), 시각컬럼=`Instant`+UTC(TS-016), 새 외부 API는 코드 前 실호출 계약검증(TS-026). **간판(지리·스코어링) 우선, 커뮤니티는 살(§9, 리뷰앱·친구망화 금지).** 침수/위험 표현 공포 조장 금지(§6). **범위 임의 확장 금지(§0-2).**
> **모델 역할 분리(규칙 E)**: 설계·검증·머지 판단은 세션 메인 모델(Opus/Fable). 단순·기계적 실작업은 **codex·agy 먼저(각 기본 코딩 모델)→토큰 소진 시 Sonnet 폴백**. 위임 결과는 메인이 검증 후 커밋.

---

## ✅ 완료: 자산화 사이클 (D1~D5) — 포트폴리오 마무리 + 무료 출시 (2026-07-11)

> **전량 완료·머지·라이브.** D5 타깃 JD 정렬(#102) → D3 아키텍처 다이어그램·라이브 데모 스크린샷(#103) → D2 /install WebAPK 원탭+iOS 홈화면 안내(#104, 프로덕션 실측) → D1 README 30초 케이스 스터디(#105) → D4 면접 STAR 9개(#106, `docs/INTERVIEW.md`). 각 항목 기능 브랜치→PR→`gh pr checks` pass 눈확인(TS-025)→squash 머지. **코드/문서 작업이라 마이그레이션·백엔드 무변경**(D2 install 페이지만 프론트 신규). 결정: **TWA 서명 APK는 후속 additive로 보류**(WebAPK 원탭이 수용기준 충족). 아래 스펙은 이력·참고용.

<details><summary>D1~D5 원 스펙(참고용)</summary>
>
> **핵심 배포 결론(2026 웹검증, 무료·포트폴리오 최적)**:
> - **안드로이드 = 무료 가능.** ① 1순위 **WebAPK**(PWA `beforeinstallprompt`→"앱 설치" 버튼→Chrome이 진짜 WebAPK 자동생성: 런처 아이콘·standalone·설정앱 등록, **사이드로딩·경고·$25·개발자검증 전부 불필요**, 브라우저 주도라 2026-09~2027 개발자검증 규제서 자유). ② 2순위 **Bubblewrap(TWA) 서명 APK 자체 호스팅 + `/.well-known/assetlinks.json`**(다운로드 가능한 `.apk` 아티팩트 — "PWA→네이티브 래핑 + 도메인 검증 이해" 강한 신호). **$25 Play 정식등록은 스킵**(이제 개발자검증 + 개인계정 12명 테스터×14일 요건 추가).
> - **iOS = 무료 공개 배포 경로가 "홈 화면에 추가"뿐(확증).** App Store·공개 TestFlight 모두 Apple $99/년 필수, `.ipa` 자체 호스팅은 일반 사용자에 불가(AltStore 류는 7일 만료·기기 3개·PC 필요라 배포 채널 아님), EU DMA는 EU 한정이라 무관. iOS 버튼 = **공유→홈화면추가 온보딩 안내**(iOS엔 자동 설치 프롬프트 없음). 설치형 PWA면 웹푸시(iOS 16.4+)·전체화면 동작.

### D1. README 쇼케이스화 — "채용담당자 30초 케이스 스터디" 【문서 · 규모 M】
- **무엇**: 현 README(status·구조 위주)를 채용담당자가 30초에 판독하는 미니 케이스 스터디로 재설계. 첫 화면에 ① 정체성 1문장 = "PostGIS 대용량 지리검색 + 실시간 UGC 시공간 스코어링" + 증명한 것 2~3불릿, ② **라이브 링크 맨 위**(geuneul.vercel.app + API), ③ 10초 데모 GIF(지도→반경검색→마커색=survival_score, D3 산출), ④ 아키텍처 다이어그램 1장(D3), ⑤ **정량 지표 배지 행**(데이터 ~14.6만 · k6 반경 p95 1.39s · JaCoCo 71% · ADR 27 · TS 32 · 공간쿼리 GiST/kNN). ERD·상세·ADR 원문은 아래로 접거나 링크.
- **현황(웹검증)**: 채용담당자 README 30초 판독·후보당 스킴 ~5분 / **라이브 데모 결정적**(84% 채용담당자 동작앱 원함) / 스택은 3~5개(더 많으면 "넓지만 얕음") / **지리공간을 프론트 지도가 아니라 DB 엔지니어링(GiST 인덱스·kNN·EXPLAIN 튜닝)으로 프레이밍**.
- **수정위치**: `README.md`(상단 재구성) · `docs/`(다이어그램 이미지/mermaid) · shields.io 배지.
- **수용기준**: 첫 스크롤에 정체성·라이브·데모·정량지표. 모든 수치는 **실측만**(과장 금지). 지리공간이 "지도 그림"이 아니라 "대용량 공간검색 엔지니어링"으로 읽힘.
- **주의**: 살(로그인·커뮤니티)을 간판처럼 쓰지 않기(§9). 라이브 링크는 반드시 살아있는지 확인 후 게시.

### D2. 무료 자체 배포 — install 페이지 + Android WebAPK/TWA APK + iOS 홈화면 안내 【프론트+빌드 · 규모 M~L】
- **무엇**: `geuneul.vercel.app/install` 페이지에서 **스토어 없이 $0**로 두 플랫폼 설치. **[Android]** = WebAPK 원탭 설치(`beforeinstallprompt` 캡처→"앱 설치" 버튼) + (아티팩트로) 다운로드 `.apk`. **[iOS]** = "공유→홈 화면에 추가" 안내 배너(iOS 감지 시). 사용자 요청("다운로드 파일 넣어두고 다운받게")을 안드로이드는 실제 충족, iOS는 정책상 홈화면 추가로 대체.
- **현황(웹검증)**: Bubblewrap CLI v1.24.1(Google 유지보수) or PWABuilder(GUI 래퍼)로 web manifest→서명 APK/AAB 생성. **자체배포는 반드시 APK**(AAB는 Play 업로드 전용, 사이드로딩 불가). TWA는 `assetlinks.json`이 keystore SHA-256과 일치해야 주소창이 사라짐(불일치면 반쪽=감점). 서명 keystore 분실 시 업데이트 불가 → `.local/`.
- **수정위치**: `frontend/app/(shell 밖?)/install/page.tsx`(신규, 플랫폼 감지·설치 버튼/안내) · `frontend/lib/` 설치 프롬프트 훅(`beforeinstallprompt` 저장) · `frontend/public/.well-known/assetlinks.json`(TWA 시) · 빌드 산출 `.apk`는 `frontend/public/` 또는 GitHub Release 호스팅 · manifest 점검(아이콘/standalone).
- **접근**: (1) WebAPK 설치 UX 먼저(경고 0·규제 자유, 1순위) → (2) iOS 안내 배너 → (3) 여력되면 Bubblewrap TWA 서명 APK + assetlinks(다운로드 아티팩트 + 파이프라인 신호). **$25/$99 안 씀.**
- **수용기준**: 안드로이드에서 /install→원탭 설치→런처 아이콘·전체화면. iOS에서 안내대로 홈화면 추가→standalone 실행. (TWA 하면) `.apk` 다운로드·설치 동작 + 주소창 없음.
- **주의**: Play Protect·"출처 알 수 없음" 경고는 사이드로딩 APK에만(비기술 리뷰어 겁줌) → **WebAPK를 메인, APK는 보조 아티팩트**. keystore·서명 비밀 `.local`(§D). 스토어 미사용이라 TS-026(외부 API) 비해당.

### D3. 데모 자산 — 아키텍처 다이어그램 + 실사용 GIF/스크린샷 【문서 · 규모 M】
- **무엇**: README·install에 박을 시각 자산. ① **아키텍처 다이어그램**(mermaid: 프론트 PWA→BFF→ALB→ECS Fargate→RDS PostGIS/ElastiCache/S3, EventBridge·LISTEN/NOTIFY). ② **10초 데모 GIF/스크린샷**: "광화문 30분 버틸 곳"(반경 검색+마커 3색), 그늘 경유 경로, 제보→상태변화 알림, 데스크톱 3분할.
- **현황**: 헤드리스 Chrome(라이브 키 있어 지도 렌더) 또는 실기기 캡처. 이전 세션이 헤드리스 1440×900 스크린샷을 이미 뽑았음(재사용/갱신).
- **수정위치**: `docs/` 또는 `frontend/public/`(이미지) · `docs/architecture.md`(mermaid).
- **수용기준**: README 첫 화면에 임베드되는 다이어그램 1장 + 데모 GIF 1~2개. 라이브 데이터 기준.
- **주의**: 용량 큰 GIF는 최적화. 개인정보·키 노출 화면 캡처 금지(§D).

### D4. 면접 STAR 스토리 — ADR/TS를 채용 자산으로 【문서 · 규모 M】
- **무엇**: 27 ADR·32 TS 중 **8~10개를 STAR(Situation-Task-Action-Result)로** 구조화한 면접 대비 문서. 후보: **TS-004**(Boot4 Jackson3↔Jackson2로 지오코딩 전량실패 + 페이크가 가린 사각지대→MockRestServiceServer) · **TS-016**(timestamptz 네이티브 프로젝션은 Instant, CI만 잡음) · **C3 RETURNING 동시성**(정확히 1회 push는 dedup_key 단위·tx 스냅샷까지) · **k6 "병목은 GiST 아니라 CPU"**(측정이 튜닝 범위를 정함) · **TS-008**(적대적 리뷰: XFF 위조 우회·eviction OOM) · **TS-009**(colima IT skip→CI가 유일 게이트) · **TS-030/031**(HikariCP 풀 캡·PG 25P02 tx 오염) · 적대적 리뷰 워크플로 방법론.
- **현황**: WORKLOG·TROUBLESHOOTING·ADR에 원천 서사 다 있음 → 선별·STAR 변환만.
- **수정위치**: `docs/INTERVIEW.md`(신규, 공개 가능 — 기술 스토리라 §D 무관) 또는 `.local/`(원하면 비공개).
- **수용기준**: 8~10개 STAR, 각 "무슨 문제→어떻게 진단→무엇을 했나→정량 결과". 면접에서 30초에 꺼낼 수 있게.
- **주의**: mp가 이미 증명한 축(분산·AI·K8s)과 중복 최소화 — 그늘 고유(지리·ETL·실시간·동시성) 위주.

### D5. 타깃 직무 JD 정렬 + README 헤드라인 튜닝 【전략/문서 · 규모 S】
- **무엇**: PORTFOLIO-CONTEXT §5 "타깃 직무 미확정" 해소. 2026 JD(당근·배민·토스·카카오모빌리티 등 위치/커머스/모빌리티 백엔드) 웹검증 → 그늘 헤드라인·강조점을 그 교집합(Spring Boot+PostgreSQL/PostGIS+테스트+캐시+부하테스트)에 정렬.
- **현황**: 메모상 "스펙은 타깃 비종속"이나 지원 시점 강조점 미정. 그늘 코어가 이미 JD 교집합과 1:1.
- **수정위치**: `README.md` 헤드라인·"무엇을 증명했나" 문구(D1과 연동) · `.local/PORTFOLIO-CONTEXT.md` §5 갱신.
- **수용기준**: 타깃 계열 1~2개 확정 + README 헤드라인이 그 JD 키워드와 정렬. 규칙B대로 웹검증 근거 WORKLOG 기록.
- **주의**: 특정 회사에 과종속 금지(스펙은 범용 유지) — 강조만 조정.

> **자산화 사이클 결정 로그**:
> - **기능 동결(2026-07-11, 사용자 합의)** — 간판·심화·완결성 충분, 추가는 한계효용 체감 + §9 리스크. 남은 건 자산화·배포.
> - **배포 = 무료·스토어 스킵(WebAPK + iOS 홈화면)** — 2026 웹검증: iOS 무료 공개배포는 홈화면추가뿐, 안드로이드는 WebAPK가 경고·규제·비용 0. $25/$99 안 씀. TWA APK는 보조 아티팩트(파이프라인 신호).
> - **README = 30초 케이스 스터디** — 라이브 링크·데모 GIF·정량 배지·아키텍처 다이어그램, 지리공간은 DB 엔지니어링으로 프레이밍.

</details>

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
