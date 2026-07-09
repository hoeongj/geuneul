# 그늘(Geuneul) — 프로젝트 현황과 다음 작업

> 현재 상태·완료분·다음 작업을 정리한 진행 문서. 전체 스펙·규칙은 [`CLAUDE.md`](./CLAUDE.md), 의사결정은 [`docs/adr/`](./docs/adr), 일지는 [`WORKLOG.md`](./WORKLOG.md), 사고기록은 [`TROUBLESHOOTING.md`](./TROUBLESHOOTING.md).
> 최종 갱신: 2026-07-10.

## ▶ 세션 인계 — 다음 세션은 여기서 시작
- **상태**: 라이브 정상(App·API). **2026-07-10 대규모 세션: P2 UGC 완결 + P3 간판강화 6개 PR 머지·배포·실측 완료.** 현재 **라이브 태스크데프 rev34**(사진 이미지 d95f37d + S3 env + SSM secret 8종 보존). Flyway **V5/V6/V7** 프로덕션 무사 적용.
- **이번 세션 라이브(6 PR, 전부 실측 검증)**: #30 날씨 comfort 복원(survival_score 기온 additive, ADR-0009) · #31 후기(review) 풀스택(영구 평판, 로그인) · #32 공부공간 데이터 확장(CAFE/STUDY_CAFE·is_commercial·deleted_at soft-delete, V5, ADR-0006) · #34 trust_score 실배선(제보 user_id 부착+가중, V6) · #33 모더레이션 flags(신고+ADMIN 검수큐, V7) · #35 S3 사진 presign(버킷 geuneul-photos-691684280989 + 태스크롤 IAM). 실측: `/weather` 24°C·`/flags` 401·`/photos/presign` S3 URL·`/places` 정상.
- **인프라 신규(이번 세션)**: terraform S3 버킷+IAM 7리소스 apply 완료. 태스크데프 rev34 수동 등록(rev32 사진 이미지 + S3_BUCKET_NAME·AWS_REGION env). ⚠️교훈: 라이브 rev를 조립할 땐 **describe 대상이 최신 이미지 rev인지 확인**(rev33이 구 flags 이미지로 조립돼 presign 404 → rev32 재조립으로 해결).
- **AI 키 확보**: `.local/ai.env`(gitignore)에 멀티프로바이더 폴백 키체인 15종(로컬 mp/myInfo + prod k3s ssuai-backend-secrets: OpenRouter·Groq·Gemini·Cerebras 등). Anthropic 키만 부재 → 곁다리 AI는 OpenRouter 프라이머리.
- **Wave 3 진행 중(2026-07-10 세션)**: C2 AI 한줄요약(OpenRouter, feat/ai-summary-p3) · C3 주기동기화(EventBridge→ECS RunTask, feat/scheduled-sync-p3) · D1 k6 부하테스트+EXPLAIN(feat/k6-load-explain-p4). **미착수**: D2 ECS 오토스케일링 · D3 관측성(OTel/Grafana). C2/C3 머지 후 SSM 키 배선(OpenRouter·serviceKey) apply 필요.
- **P3 날씨 라이브(PR #26 + 핫픽스 #28·#29)**: `GET /weather?lat=&lng=` — 기상청 초단기실황(getUltraSrtNcst) + 격자변환 + **ElastiCache Redis TTL 캐시(30분) 실동작**. 프로덕션 실측: 광화문 `지금 23°C, 비`, 부산 `31°C`, 캐시 히트 정상. serviceKey는 `.local/datago.env`(data.go.kr 계정 공통 키). **하드닝: 두 캐시 버그 배포 중 발견·수정 — TS-011(@Cacheable Optional 언랩 SpEL), TS-012(무타이핑 직렬화 캐시히트 500). 회귀 테스트 2건 추가.**
- **P2 소셜 로그인 라이브·검증 완료(PR #27)**: 카카오/구글 OAuth2(BFF code 서버교환) + JWT + `/me` + 프론트 "내 정보" 탭. **브라우저 실사용 성공 — 구글·카카오 둘 다 프로필까지 표시**(홍성주/카카오·구글). 카카오는 콘솔 설정 3건으로 지연됐다(TS-013): Redirect URI를 로그아웃 칸에 잘못 등록·호출허용IP 127.0.0.1·Client Secret ON 배선. 키는 `.local/oauth.env`·SSM·Vercel env(KAKAO_REST_API_KEY·GOOGLE_CLIENT_ID).
- **인프라 신규**: ElastiCache Redis(`cache.t3.micro`, 프리티어 대상) + SSM 6종(kma/kakao_rest/kakao_secret/google×2/jwt). `elasticache.tf`·`ssm.tf`·`ecs.tf`. **참고: Redis를 지워도 CacheErrorHandler로 날씨는 기상청 직접호출로 계속 동작(무료화 시 코드변경 0).**
- **콘솔 없이 바로 가능한 다음** (~~날씨 2부 #30·후기 #31·trust #34 완료~~):
  1. **D2 ECS 오토스케일링** — Service Auto Scaling(=HPA 상당), D1 k6 부하와 함께(P4). terraform.
  2. **D3 관측성** — OTel/Grafana(P4).
  3. **쉼터 전국 전체 데이터**(행안부 safetydata) 재적재 + 화장실 실패 7,193건 재시도.
  4. **ALB HTTPS**(ACM+도메인+443) — 공유 링크 신뢰도.
- **§⑤ 공부공간 데이터 확장([ADR-0006])**: data.go.kr 키 확보됨(`.local/datago.env`). 전국도서관표준데이터는 **CSV 다운로드**로 적재(오픈API는 경기도만) / 상권정보(카페·스터디카페)는 오픈API. `PlaceCategory`+CAFE/STUDY_CAFE·스키마(is_commercial·deleted_at)·파서·실적재를 실데이터로 한 번에.
- **배포 접근(확인됨)**: 로컬에 AWS CLI(`geuneul-admin`)·Vercel CLI(`akftjdwn-9388`) 모두 인증돼 있어 Claude가 직접 배포 가능. 태스크데프 config는 `ignore_changes`라 라이브 rev는 수동 등록(describe→env/secret 추가→register→update-service). deploy.yml은 이미지만 교체(describe 기반)라 config 보존.
- **작업 규칙 리마인더**: 커밋 신원 `ghdtjdwn`/`seongjuice999@gmail.com`, 푸시 전 비밀 스캔, 비밀은 `.local`·`.env`만, 결정은 WORKLOG에 why/대안 기록(CLAUDE.md §A~D).

## 최근 완료 · 다음 작업

**최근 완료(전부 라이브·검증됨):**
- ✅ **P2 제보(reports) 기능 풀스택** — 백엔드 API(`POST /reports`·`GET /places/{id}/reports`, 익명·타입별 TTL, PR #15) + 프론트 실전송/상세 최근제보(PR #16) + 상세 미니맵 실지도(PR #14). 라이브 E2E 통과.
- ✅ **다중 에이전트 적대적 코드리뷰**로 확정 결함 2건 하드닝(PR #17, TS-008): 레이트리밋 XFF 위조 우회 + eviction OOM.
- ✅ **포트폴리오 품질 감사**로 코드·인프라·문서 32건 개선(거리 이중계산 제거·레이트리미터 원자화·배포 테스트게이트·gitleaks·SHA-pin 등, PR #18–21).
- ✅ 문서: ADR-0004(BFF 프록시)·TS-006/007/008·WORKLOG.

**다음 작업 (외부 콘솔·인프라 권한이 필요한 항목):**

### ① ~~레이트리밋 proxy-secret 활성화~~ — ✅ **완료(2026-07-04)**
XFF 위조 우회가 **완전 차단**됐다. 한 일:
- 시크릿 `openssl rand -hex 32` → `.local/proxy-secret.env`(gitignore).
- **백엔드**: `ssm.tf`에 `aws_ssm_parameter.proxy_secret`(SecureString) + `variables.tf` `proxy_secret`(sensitive) + `terraform.tfvars`(gitignore) 값 → `terraform apply`(1 add, IAM은 `/geuneul/*` 와일드카드라 무변경). 태스크데프는 `ignore_changes[container_definitions]`라 TF로 안 먹어서, **실행 리비전 기반 새 rev(13) 등록 + `update-service` 강제 재배포**(이미지 보존). `ecs.tf` secrets엔 문서화용으로 추가(다음 fresh apply 대비).
- **프론트**: `vercel env add GEUNEUL_PROXY_SECRET production` + `vercel redeploy`. proxyPost가 `X-Proxy-Auth`+`X-Client-Ip` 전송.
- **검증(헤더 고정, egress 무관)**: 유효 시크릿+고정 X-Client-Ip → 4번째 429(유저별 리밋 ✓) / 틀린 시크릿+위조 X-Client-Ip → 무시하고 최우측 키잉 → 4번째 429(우회 차단 ✓). 상세 TS-008.
- **재활성/회전 시**: 시크릿 값은 `.local/proxy-secret.env`·SSM `/geuneul/proxy_secret`·Vercel env 3곳 동일. 회전하려면 세 곳 갱신 후 백엔드 `update-service --force-new-deployment`.

### ② OAuth 소셜 로그인 준비(P2 다음 조각 — 후기·신뢰도의 선행)
- **카카오**: 콘솔 앱(지도와 **같은 앱 재사용 가능**) → 카카오 로그인 **활성화 ON** + **Redirect URI** 등록(`https://geuneul.vercel.app/api/auth/kakao/callback` 및 로컬). **REST API 키 + Client Secret**(보안 강화) 발급 → `.local`.
- **구글**: [console.cloud.google.com](https://console.cloud.google.com) → OAuth 동의화면 + 사용자 인증정보(OAuth 2.0 클라이언트 ID, 웹) → 승인 리디렉션 URI 등록 → Client ID/Secret → `.local`.
- 준비되면 알려주면 백엔드 OAuth2+JWT(mp 레퍼런스) → 프론트 로그인 플로우 착수. 키는 전부 `.local`·SSM·Vercel env로만(규칙 D).

### ③ 그 밖에 바로 이어갈 수 있는 것(로그인 불필요)
- **후기(review)** 백엔드 — 단, 후기는 "로그인 필요"라 OAuth 다음이 자연스러움. 익명 불가.
- ~~**survival_score(P3)** — SQL 랭킹 + 마커 3색.~~ **완료(ADR-0007).**
- ~~**추천(/recommendations, P3)** — survival_score에 시나리오 가중.~~ **완료·라이브(PR #24, ADR-0008).**
- **날씨 API(P3)** — 기상청 초단기예보 + Redis TTL 캐시. survival_score/추천의 open_now·기온 결측 성분을 복원(재정규화 되돌림). 콘솔 불필요(기상청 공공 API), 바로 착수 가능.
- **AI 한줄 요약(P3)** — Claude API(곁다리). 상세 화면 예약 슬롯.
- **S3 사진 업로드(presign)** — Terraform S3 + presign API. 제보/후기 사진 슬롯 대기.

### ④ 카공/카페 기능 흡수 (제안 — 방향 확인 후 착수, [ADR-0005](./docs/adr/0005-cafe-features-as-summer-scenario.md))
카공맵류 조사 결과, 카공은 그늘 "여름 실내 오래 버티기"의 부분집합 → **간판 강화 방향으로만 흡수**(리뷰앱·카페앱화 거부). 우선순위:
- [x] ~~**[간판·최우선] 실시간 자리 여유/혼잡 제보**~~ — **완료·라이브(PR #22).** `ReportType`에 `SEAT_OK`('자리 있어요')·`CROWDED`('붐벼요') 추가(스키마 무변경, TTL 2h). 제보 그리드 맨 앞. survival_score(P3)로 굴러갈 "지금 앉을 수 있음" 신호. 여름 라벨 유지.
- [x] ~~**[간판] survival_score(P3)** 우선 구현 — 카공 capacity score로 단일 종합점수 수요 재검증.~~ — **완료(ADR-0007).** SEAT_OK/CROWDED가 comfort(+)/risk(−) 신호로 survival_score에 반영돼 "지금 앉을 수 있음"이 종합점수·마커색으로 굴러간다.
- [ ] **[간판·차별점] GPS 방문 인증** — `ST_DWithin`(100m)+사진 EXIF로 `verified` → trust_score 가중(허위제보 억제). reports/reviews에 `verified` boolean 소폭 확장(P2→P4).
- [ ] **[살·즉효] place_features.value 등급화** — 콘센트=개수/접근성, wifi=속도, `noise_level` 추가(스키마 변경 없음, comfort_score 정밀화).
- [ ] **[간판 확장·P4] 시간대별 혼잡 파생** — reports 이력 요일×시간 집계 → 자체 popular-times(외부 API 불필요).
- [ ] **[살] 추천 시나리오 `focus`/`longstay`** 추가(파라미터만) · 정형 태그 리뷰.
- **거부/보류(정체성 희석·비목표):** aspect 별점 UI 주인공·예약/결제·리워드·소셜 팔로우·가격필터·좌석단위 콘센트 지도. (CAFE 카테고리는 사용자 승인 → [ADR-0006]으로 **제안(Proposed)** — enum·스키마는 아직 미반영(serviceKey 대기), 아래 §⑤.)

### ⑤ 공부 가능 공간 데이터 확장 (제안 — 대량 적재는 serviceKey 확보 후, [ADR-0006](./docs/adr/0006-study-space-coverage-expansion.md))
사용자 요청: "공부 가능한 카페 + 공공 공부공간(노들서가류) 전부 넣고 싶다" = **데이터 커버리지 확장**(간판 ETL 강화, §3 커버리지 원칙 정합). 카공 UGC 기능(§④)과 별개.
- **설계**: category 최소 신설(`CAFE`/`STUDY_CAFE` 2개) · '공부 가능'은 `place_features(study_ok/quiet)` 속성 · 상업/커먼스 분리 `places.is_commercial` · 폐업 회전 대응 `deleted_at` soft-delete(로드맵 P3 앞당김).
- **소스 우선순위**: 전국도서관표준(WGS84, 最易) → 상권정보 STUDY_CAFE(1만) → 상권정보 카페(9만, 지오코딩 0) → 공공시설개방(CIVIC) → 명소 시드(노들서가 등). LOCALDATA는 EPSG:5174 재투영 부담이라 폐업 검증 보조만.
- **카페 study_ok**: 공공데이터에 없음 → 전량 적재 + UGC 태깅(카공맵도 100% 크라우드소싱).
- 🔴 **블로커**: 대량 적재는 **공공데이터포털 오픈API serviceKey(무료)** 또는 다운로드 CSV 필요. odcloud/상권정보 API는 키 없이 401, 서울열린데이터는 sample 5행만. serviceKey면 P3 무인화(EventBridge→ECS RunTask)까지 연결. **확보 시 enum+스키마+파서+적재를 실데이터로 한 번에.**

---

## 지금 상태 — P1(지리 코어) 완결 + 프론트 MVP 라이브 + P2 제보 라이브 + P3 survival_score·추천 라이브

🟢 **API Live:** http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com (`/actuator/health`, `/swagger-ui.html`)
🟢 **App Live:** https://geuneul.vercel.app (프론트 PWA — Kakao 실지도 + 라이브 데이터 + 실시간 제보)

- **백엔드:** Spring Boot 4.0.6 / Java 21. 반경(`ST_DWithin` geography)·최근접(kNN `<->`)·bounds 공간검색 API 라이브. `backend/`.
- **인프라:** AWS ECS Fargate + RDS PostgreSQL(PostGIS) + **ElastiCache Redis** + Terraform(IaC) + GitHub Actions OIDC + ECR + ALB. `main`에 `backend/**` push 시 자동배포. **현재 라이브 태스크데프 rev26**(날씨+로그인 배선: REDIS_HOST env + SSM secret 8종=DB·PROXY·KMA·KAKAO_REST·KAKAO_SECRET·GOOGLE_ID·GOOGLE_SECRET·JWT). rev25=캐시 핫픽스, rev23=ElastiCache 배선, rev20=품질 하드닝 PR #25(과거값). `infra/`.
- **데이터(프로덕션 RDS):** 무더위쉼터 100건(전국 샘플) + 공중화장실 **46,897건**(카카오 지오코딩). 광화문·대전·부산·강릉 라이브 검증 통과.
- **P2 제보(라이브, PR #15·#16·#17):** 익명 휘발성 제보 `POST /reports`(타입별 TTL로 `expires_at`) + `GET /places/{id}/reports`. 프론트 제보하기 실전송(장소=nearest+피커)·상세 "최근 제보" 실시간. 인메모리 레이트리밋(분3·시간10) — XFF 신뢰경계(`ProxyClientResolver`)·OOM 하드닝(TS-008).
- **P3 survival_score(라이브, PR #23·ADR-0007):** `place_report_signals` 뷰(V4)가 유효제보를 최근성×신뢰도로 집계(freshness/comfort/risk) → 순수 함수 `SurvivalScore`가 §5 가중치 조립·등급(GOOD/OKAY/UNKNOWN). `/places`(반경·bounds)·`/places/{id}` 응답에 `survival` 필드. 프론트 마커 3색 링·리스트/상세 상태 배지. open_now는 운영시간 결측이라 재정규화 제외(데이터 붙으면 복원), 후기는 §5대로 분리. **로컬 검증: postgis에 V1~V4 직접 적용 + 시나리오별 뷰/쿼리 실행으로 시맨틱 확증(TS-009), 엔드투엔드 IT는 CI.**
- **P3 추천(라이브, PR #24·ADR-0008):** `GET /recommendations?scenario=rest30|restroom|rain` — survival_score 순수 함수를 시나리오 가중치로 재사용(`SurvivalScore.Weights` 오버로드) + 2단 검색(공간 인덱스 선필터 `findWithinRadiusScoredByCategories` → 앱 재랭킹). 응답=canonical `survival` 배지 + `matchScore`(적합도, 정렬 기준) + `reason`(제보 요약). 가중치: rest30 comfort↑(0.35) / restroom distance 압도(0.60) / rain risk↑(0.40, 침수 강등 — 배지는 §6대로 −0.15 순화 유지). 프론트 "급해요"를 nearest 팬아웃 → 정식 랭킹으로 승격(`/api/urgent`가 `/recommendations` 프록시). **검증: 단위 15건 green + 엔드투엔드 IT 3건은 colima 이슈로 로컬 skip→CI green 후 머지·배포. 프로덕션 실측(광화문 화장실 거리순 matchScore, 상도동 rest30에서 제보 있는 도서관이 무제보 쉼터 앞섬).**
- **프론트(완료, PR #12·#14·#16):** Next.js 16(App Router)+TS PWA — MVP 4화면(홈 지도·장소 상세(실지도 미니맵·최근제보)·급해요·제보 실전송). **동일 오리진 서버 프록시(BFF)** 로 ALB(http)·CORS 회피(ADR-0004) → **백엔드 CORS 불필요**. Vercel git 연결로 `main` push 시 자동배포(rootDirectory=frontend). Kakao JS 키는 콘솔 **[JavaScript 키 > JavaScript SDK 도메인]** 에 `https://geuneul.vercel.app` 등록 완료(제품링크관리>웹도메인과 다른 칸 — 혼동 주의).
- **테스트:** 파서/컨트롤러/지오코딩 단위 + 실 PostGIS IT(멱등·공간쿼리). JaCoCo floor 0.35 ratchet. CI(`ci.yml`)가 실 PostGIS로 검증. 프론트는 `frontend-ci.yml`(typecheck·lint·build).

## 아키텍처 한눈에
```
backend/    Spring Boot 4 — domain.place(공간검색·survival) · domain.report(제보) · domain.recommend(추천) · domain.ingest(+geocode) · global.geo/config
infra/      terraform/(VPC·RDS·ECS·ALB·ECR·IAM OIDC) · scripts/prod-ingest.sh
frontend/   Next.js 16 App Router PWA — app/(shell)(4화면) · app/api(서버 프록시 BFF) · components · lib
docs/       adr/0001~0008 · design-brief.md
.github/    ci.yml(백엔드 테스트) · deploy.yml(OIDC 배포, paths=backend/**) · frontend-ci.yml(paths=frontend/**)
.local/     (gitignore) myInfo·PORTFOLIO-CONTEXT — 비밀·회사매핑
```

## 운영 치트시트
- **배포:** `main`에 `backend/**` 머지 → 자동. 문서·인프라만 바뀐 push는 배포 트리거 안 됨(paths 필터).
- **공공데이터 재적재(멱등):** `KAKAO_REST_API_KEY=<키> ./infra/scripts/prod-ingest.sh public_toilet <릴리즈URL> MS949` (쉼터는 키 불필요·UTF-8). 데이터 스냅샷은 GitHub Release `data-v1`.
- **인프라 변경:** `cd infra/terraform && terraform plan/apply` (tfvars·tfstate는 gitignore). 전체 삭제 = `terraform destroy`.
- **비밀 위치:** 카카오/AWS/서버 키는 전부 `.local/`·`~/.aws`·env로만. 레포 커밋 금지(규칙 D). 커밋 전 유출 스캔 필수.
- **비용:** ALB ~$16/월 + Fargate(0.25vCPU/1GB) ~$12/월. RDS·ECS·SSM·ECR 사실상 무료. $200 크레딧 + 예산 알림(실지출 $0.01↑ 메일).

## 다음 할 일 (우선순위)

### P2 · UGC + 인증 (진행 중)
- [x] ~~**제보(report)**: `POST /reports` 휘발성(`expires_at`, 타입별 TTL). 익명 허용~~ — **완료·라이브(PR #15·#16·#17).** 백엔드 API + 프론트 실전송 + 상세 최근제보. 레이트리밋 XFF 신뢰경계·OOM 하드닝(TS-008). `GET /places/{id}/reports`도.
- [ ] **소셜 로그인**: 카카오/구글 OAuth2 + JWT 세션. (Boot 4 `spring-boot-starter-oauth2-client`/`-resource-server`, jjwt. mp에 레퍼런스.) **선행: 카카오·구글 콘솔 앱 설정(아침 체크리스트).**
- [ ] **후기(review)**: `POST /reviews`·`GET /places/{id}/reviews` 영구 평판(별점/코멘트/사진). 로그인 필요 → OAuth 다음.
- [ ] **사진 업로드**: `POST /photos/presign` — **S3 버킷 + presigned URL**(terraform에 S3 추가 필요, 태스크 롤에 s3 권한). 프론트 제보 화면 사진 슬롯이 대기.
- [ ] **신뢰도(trust_score)** 계산 + 제보 가중(로그인 제보에). 로그인 시 `reports.user_id`·`is_anonymous=false` 경로는 엔티티에 이미 준비됨.
- [ ] **모더레이션 큐**: `POST /flags` 신고 + `GET /admin/flags/pending`(관리자).
- [x] ~~**레이트리밋 proxy-secret 활성화**~~ — **완료(2026-07-04).** BFF 공유시크릿 라이브, XFF 위조 우회 차단 검증(TS-008, 체크리스트 §1).
- 프론트 예약 슬롯: 최근 제보·**survival_score 마커 3색·상태 배지=완료**. 후기·로그인 배지·AI 요약은 여전히 대기.

### P3 · 스코어·추천·AI
- [x] ~~**survival_score**: 거리+open_now+comfort+freshness−risk를 **SQL/PostGIS 레이어에서** 계산(CLAUDE.md §5). 마커 3색.~~ — **완료(ADR-0007).** `place_report_signals` 뷰(V4)가 유효제보를 최근성×신뢰도로 집계(freshness/comfort/risk), 순수 함수 `SurvivalScore`가 §5 가중치 조립·등급(GOOD/OKAY/UNKNOWN). 반경/bounds/단건 스코어드 API + 프론트 마커 3색·상태 배지. open_now는 운영시간 결측이라 재정규화로 제외(데이터 붙으면 복원). 후기는 §5대로 분리.
- [x] ~~**추천 시나리오**: `GET /recommendations?scenario=rest30|restroom|rain`.~~ — **완료·라이브(PR #24, ADR-0008).** survival_score 순수 함수를 시나리오 가중치로 재사용(SurvivalScore.Weights 오버로드) + 2단 검색(공간 인덱스 선필터 → 시나리오 재랭킹). 응답에 canonical `survival` 배지 + `matchScore`(적합도) + `reason`(제보 요약). 프론트 "급해요"를 nearest 팬아웃 → 정식 랭킹으로 승격. 콘솔 불필요라 바로 착수했음.
- [ ] **날씨**: 기상청 초단기예보 + **Redis TTL 캐시**(rate limit). Redis 헬스체크 다시 켜기(application.yml `management.health.redis.enabled`). **다음 콘솔 불필요 조각** — open_now/기온을 붙이면 survival_score/추천 재정규화를 additive 복원.
- [ ] **AI 한줄 요약**: Claude API(곁다리).
- [ ] **공공데이터 주기 동기화**: EventBridge Scheduler → ECS RunTask(월1회 등). 멱등 upsert 재실행 + **스냅샷에서 사라진 행 soft-delete 비활성화**(폐쇄 반영) + **오픈API serviceKey로 다운로드까지 무인화**(현재 수동 다운로드 병목 제거).
- [ ] **쉼터 전국 전체 데이터** 확보(행안부 safetydata) 후 재적재. 화장실 실패 7,193건 재시도.

### P4 · 심화 (간판 보강)
- [x] ~~**k6 부하테스트 + EXPLAIN 인덱스 튜닝**(반경/kNN 성능 실증)~~ — **완료(ADR-0010, 브랜치 `feat/k6-load-explain-p4`).** 로컬 docker-compose PostGIS에 합성 30만 places + 21만 reports 시드 → EXPLAIN으로 반경(`ST_DWithin` geography GiST)·kNN(`<->` GiST)·bounds(geometry GiST) 인덱스 사용 확증 + k6 4엔드포인트 부하 실측(green, kNN p95 213ms·반경 p95 1.4s·실패율 0%). **Flyway V8** `idx_reports_expires`로 `place_report_signals` 뷰의 만료 제보 누적 전체스캔 튜닝(뷰빌드 256→133ms). `perf/*`(k6·seed·explain·RESULTS). ⚠️ 로컬 PostGIS가 amd64 emulated라 절대 지연은 부풀려짐(실행계획·before/after 비율·처리량 상한만 유효, 프로덕션 RDS 미측정).
- [ ] **ECS Service Auto Scaling**(=HPA 상당) 부하와 함께.
- [ ] 실시간 이벤트(제보 급증 알림 — Redis Streams/LISTEN·NOTIFY), 캐시 전략, 관측성(OTel/Grafana), ADR 계속.

### 인프라/프론트 백로그
- [ ] **ALB HTTPS**(ACM 인증서 + 도메인 + 443 리스너) — 프론트가 서버 프록시를 안 쓰고 직접 붙거나, 공유 링크 신뢰도 위해.
- [ ] 트래픽 붙으면 **Fargate task_cpu 512**로(부팅 93초 단축, TS-005 근본 해결).
- [x] ~~프론트 Vercel 배포 + Kakao JavaScript 키~~ — **완료(2026-07-03).** https://geuneul.vercel.app 라이브, git 자동배포 연결. 도메인은 콘솔 [JavaScript SDK 도메인] 칸(TS-007·WORKLOG 참고).

## 트러블슈팅 요약 (전부 `TROUBLESHOOTING.md`에 상세)
- **TS-001** SG description ASCII 전용 → 영어. (`terraform validate`는 API 값제약 못 잡음)
- **TS-002** `@Container`(static) × Spring 컨텍스트 캐시 수명주기 충돌 → 싱글턴 컨테이너.
- **TS-003** 빨간 CI 머지→크래시루프→ECS 런치 백오프 → 배포 서킷브레이커+롤백, 머지 게이트 exit code 판정.
- **TS-004** Boot 4(Jackson 3) ↔ 코드 Jackson 2 `JsonNode`로 지오코딩 전량 실패(0건) + 페이크 지오코더가 실 파싱 계약을 가린 사각지대 → record + MockRestServiceServer.
- **TS-005** 느린 부팅(93s, 0.25vCPU) > 헬스체크 유예(120s) → 서킷브레이커 오롤백 → 유예 240초.
- **TS-006** Next 16 기본 Turbopack ↔ Serwist(webpack 플러그인) 충돌 + ESLint 10 신판 러그 → 빌드 webpack 고정·ESLint 9 핀.
- **TS-007** 로컬 경고 억제용 `outputFileTracingRoot`가 Vercel git 빌드(rootDirectory 기반)의 출력 수집을 깨서 ENOENT → `process.env.VERCEL` 가드.
- **TS-008** 적대적 리뷰 확정: 레이트리밋 XFF 최좌측 신뢰(ALB append라 위조 가능)→우회 + eviction no-op→OOM → ProxyClientResolver(BFF 시크릿 신뢰경계)+evict 하드상한.
- **TS-009** 로컬 Testcontainers가 colima에서 전멸 skip(docker-java API 1.32↔엔진 1.40 + 소켓/DOCKER_HOST 미상속) → 하네스 대신 postgis에 V1~V4 직접 적용해 뷰 SQL을 시나리오별로 실증, IT는 CI에 위임. "skip을 통과로 오독 금지".
> 공통 교훈: ① "Docker 있는 CI에서만 드러나는 실패"는 로컬 green→CI red → 반드시 CI green 확인 후 머지. ② 모킹은 "우리 로직"은 검증하되 "외부와의 실제 계약"을 가린다.

## 워크플로우 리마인더 (CLAUDE.md 필수 규칙)
- **커밋 신원** `ghdtjdwn`/`seongjuice999@gmail.com` (레포 config 고정, 커밋 전 확인).
- **의사결정**은 웹으로 2026 트렌드 확인 + `WORKLOG.md`에 무엇/왜/대안/근거 기록. 사고는 `TROUBLESHOOTING.md`.
- **기능 브랜치 + PR + CI green 확인 후 머지**(`gh run watch --exit-status`의 실제 exit code로 판정 — 파이프 tail 오판 금지).
- **비밀은 대화 OK, git 금지**(규칙 D). `.local/`·`.env`·tfvars/tfstate gitignore. 커밋 전 스캔.
- **범위 임의 확장 금지** — 새 기능은 제안 후 확인. 간판(지리공간)이 주인공, 커뮤니티는 살.

## 작업 원칙 (git)
기능 브랜치 + PR + CI green 확인 후 머지. 병행 작업 시 `git add -A` 대신 **특정 파일만 스테이징**하고, 로컬 `main`에서 직접 작업하지 않는다(다른 작업의 미커밋 변경을 삼키거나 커밋이 섞이는 것을 방지).
