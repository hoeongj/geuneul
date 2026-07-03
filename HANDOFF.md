# HANDOFF — 그늘(Geuneul) 이어서 작업하기

> 새 세션이 여기서부터 이어간다. 전체 스펙·워크플로우 규칙은 [`CLAUDE.md`](./CLAUDE.md)(자동 로드), 의사결정은 [`docs/adr/`](./docs/adr), 일지는 [`WORKLOG.md`](./WORKLOG.md), 사고기록은 [`TROUBLESHOOTING.md`](./TROUBLESHOOTING.md).
> 최종 갱신: 2026-07-04(새벽 자동세션).

## 🌅 아침 체크리스트 (밤사이 한 일 + 네가 할 것)

**밤사이 자동 완료(전부 라이브·검증됨):**
- ✅ **P2 제보(reports) 기능 풀스택** — 백엔드 API(`POST /reports`·`GET /places/{id}/reports`, 익명·타입별 TTL, PR #15) + 프론트 실전송/상세 최근제보(PR #16) + 상세 미니맵 실지도(PR #14). 라이브 E2E 통과.
- ✅ **적대적 다중 에이전트 코드리뷰**(19 에이전트) → 확정 결함 2건 하드닝(PR #17, TS-008): 레이트리밋 XFF 위조 우회 + eviction OOM.
- ✅ 문서: ADR-0004(BFF 프록시)·TS-006/007/008·WORKLOG.

**네가 직접 해야 하는 것(콘솔/인프라 권한·판단 필요라 자동으로 안 함):**

### ① ~~레이트리밋 proxy-secret 활성화~~ — ✅ **완료(2026-07-04 새벽, 자동)**
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
- **survival_score(P3)** — 제보 freshness 데이터가 이제 쌓이므로 SQL 랭킹 착수 가능(로그인 불필요). 마커 3색.
- **S3 사진 업로드(presign)** — Terraform S3 + presign API. 제보/후기 사진 슬롯 대기.

---

## 지금 상태 — P1(지리 코어) 완결 + 프론트 MVP 라이브 + P2 제보 라이브

🟢 **API Live:** http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com (`/actuator/health`, `/swagger-ui.html`)
🟢 **App Live:** https://geuneul.vercel.app (프론트 PWA — Kakao 실지도 + 라이브 데이터 + 실시간 제보)

- **백엔드:** Spring Boot 4.0.6 / Java 21. 반경(`ST_DWithin` geography)·최근접(kNN `<->`)·bounds 공간검색 API 라이브. `backend/`.
- **인프라:** AWS ECS Fargate + RDS PostgreSQL(PostGIS) + Terraform(IaC) + GitHub Actions OIDC + ECR + ALB. `main`에 `backend/**` push 시 자동배포. `infra/`.
- **데이터(프로덕션 RDS):** 무더위쉼터 100건(전국 샘플) + 공중화장실 **46,897건**(카카오 지오코딩). 광화문·대전·부산·강릉 라이브 검증 통과.
- **P2 제보(라이브, PR #15·#16·#17):** 익명 휘발성 제보 `POST /reports`(타입별 TTL로 `expires_at`) + `GET /places/{id}/reports`. 프론트 제보하기 실전송(장소=nearest+피커)·상세 "최근 제보" 실시간. 인메모리 레이트리밋(분3·시간10) — XFF 신뢰경계(`ProxyClientResolver`)·OOM 하드닝(TS-008).
- **프론트(완료, PR #12·#14·#16):** Next.js 16(App Router)+TS PWA — MVP 4화면(홈 지도·장소 상세(실지도 미니맵·최근제보)·급해요·제보 실전송). **동일 오리진 서버 프록시(BFF)** 로 ALB(http)·CORS 회피(ADR-0004) → **백엔드 CORS 불필요**. Vercel git 연결로 `main` push 시 자동배포(rootDirectory=frontend). Kakao JS 키는 콘솔 **[JavaScript 키 > JavaScript SDK 도메인]** 에 `https://geuneul.vercel.app` 등록 완료(제품링크관리>웹도메인과 다른 칸 — 혼동 주의).
- **테스트:** 파서/컨트롤러/지오코딩 단위 + 실 PostGIS IT(멱등·공간쿼리). JaCoCo floor 0.35 ratchet. CI(`ci.yml`)가 실 PostGIS로 검증. 프론트는 `frontend-ci.yml`(typecheck·lint·build).

## 아키텍처 한눈에
```
backend/    Spring Boot 4 — domain.place(공간검색) · domain.ingest(+geocode) · global.geo/config
infra/      terraform/(VPC·RDS·ECS·ALB·ECR·IAM OIDC) · scripts/prod-ingest.sh
frontend/   Next.js 16 App Router PWA — app/(shell)(4화면) · app/api(서버 프록시 BFF) · components · lib
docs/       adr/0001~0004 · design-brief.md
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
- 프론트 예약 슬롯: 최근 제보=**완료**. 후기·로그인 배지·AI 요약은 여전히 대기.

### P3 · 스코어·추천·AI
- [ ] **survival_score**: 거리+open_now+comfort+freshness−risk를 **SQL/PostGIS 레이어에서** 계산(CLAUDE.md §5 공식). 마커 3색(초록/노랑/회색) 구간.
- [ ] **추천 시나리오**: `GET /recommendations?scenario=rest30|restroom|rain`.
- [ ] **날씨**: 기상청 초단기예보 + **Redis TTL 캐시**(rate limit). Redis 헬스체크 다시 켜기(application.yml `management.health.redis.enabled`).
- [ ] **AI 한줄 요약**: Claude API(곁다리).
- [ ] **공공데이터 주기 동기화**: EventBridge Scheduler → ECS RunTask(월1회 등). 멱등 upsert 재실행 + **스냅샷에서 사라진 행 soft-delete 비활성화**(폐쇄 반영) + **오픈API serviceKey로 다운로드까지 무인화**(현재 수동 다운로드 병목 제거).
- [ ] **쉼터 전국 전체 데이터** 확보(행안부 safetydata) 후 재적재. 화장실 실패 7,193건 재시도.

### P4 · 심화 (간판 보강)
- [ ] **k6 부하테스트 + EXPLAIN 인덱스 튜닝**(반경/kNN 성능 실증) — mp에 k6 레퍼런스.
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
> 공통 교훈: ① "Docker 있는 CI에서만 드러나는 실패"는 로컬 green→CI red → 반드시 CI green 확인 후 머지. ② 모킹은 "우리 로직"은 검증하되 "외부와의 실제 계약"을 가린다.

## 워크플로우 리마인더 (CLAUDE.md 필수 규칙)
- **커밋 신원** `hoengj`/`seongjuice999@gmail.com` (레포 config 고정, 커밋 전 확인).
- **의사결정**은 웹으로 2026 트렌드 확인 + `WORKLOG.md`에 무엇/왜/대안/근거 기록. 사고는 `TROUBLESHOOTING.md`.
- **기능 브랜치 + PR + CI green 확인 후 머지**(`gh run watch --exit-status`의 실제 exit code로 판정 — 파이프 tail 오판 금지).
- **비밀은 대화 OK, git 금지**(규칙 D). `.local/`·`.env`·tfvars/tfstate gitignore. 커밋 전 스캔.
- **범위 임의 확장 금지** — 새 기능은 제안 후 확인. 간판(지리공간)이 주인공, 커뮤니티는 살.

## 동시 작업 주의
프론트 세션 작업은 **완료·머지됨**(PR #12·#14) — 현재 브랜치 충돌 요소 없음. 다만 여러 세션이 병행될 땐 원칙 유지: **자체 기능 브랜치 + 특정 파일만 add**(`git add -A` 금지 — 상대 세션의 미커밋 작업을 삼킬 수 있음), 로컬 main 위에서 직접 작업 금지.
