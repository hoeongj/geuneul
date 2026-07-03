# HANDOFF — 그늘(Geuneul) 이어서 작업하기

> 새 세션이 여기서부터 이어간다. 전체 스펙·워크플로우 규칙은 [`CLAUDE.md`](./CLAUDE.md)(자동 로드), 의사결정은 [`docs/adr/`](./docs/adr), 일지는 [`WORKLOG.md`](./WORKLOG.md), 사고기록은 [`TROUBLESHOOTING.md`](./TROUBLESHOOTING.md).
> 최종 갱신: 2026-07-03.

## 지금 상태 — P1(지리 코어) 완결, 라이브

🟢 **Live:** http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com (`/actuator/health`, `/swagger-ui.html`)

- **백엔드:** Spring Boot 4.0.6 / Java 21. 반경(`ST_DWithin` geography)·최근접(kNN `<->`)·bounds 공간검색 API 라이브. `backend/`.
- **인프라:** AWS ECS Fargate + RDS PostgreSQL(PostGIS) + Terraform(IaC) + GitHub Actions OIDC + ECR + ALB. `main`에 `backend/**` push 시 자동배포. `infra/`.
- **데이터(프로덕션 RDS):** 무더위쉼터 100건(전국 샘플) + 공중화장실 **46,897건**(카카오 지오코딩). 광화문·대전·부산·강릉 라이브 검증 통과.
- **프론트:** 다른 세션이 `feat/frontend-mvp`에서 Next.js 16 PWA로 MVP 4화면 구현 중(`frontend/`). 서버 프록시(`/api/*`)로 ALB(http)·CORS 회피 → **백엔드 CORS 불필요**.
- **테스트:** 파서/컨트롤러/지오코딩 단위 + 실 PostGIS IT(멱등·공간쿼리). JaCoCo floor 0.35 ratchet. CI(`ci.yml`)가 실 PostGIS로 검증.

## 아키텍처 한눈에
```
backend/    Spring Boot 4 — domain.place(공간검색) · domain.ingest(+geocode) · global.geo/config
infra/      terraform/(VPC·RDS·ECS·ALB·ECR·IAM OIDC) · scripts/prod-ingest.sh
frontend/   Next.js 16 App Router (다른 세션)
docs/       adr/0001~0003 · design-brief.md
.github/    ci.yml(테스트) · deploy.yml(OIDC 배포, paths=backend/**)
.local/     (gitignore) myInfo·PORTFOLIO-CONTEXT — 비밀·회사매핑
```

## 운영 치트시트
- **배포:** `main`에 `backend/**` 머지 → 자동. 문서·인프라만 바뀐 push는 배포 트리거 안 됨(paths 필터).
- **공공데이터 재적재(멱등):** `KAKAO_REST_API_KEY=<키> ./infra/scripts/prod-ingest.sh public_toilet <릴리즈URL> MS949` (쉼터는 키 불필요·UTF-8). 데이터 스냅샷은 GitHub Release `data-v1`.
- **인프라 변경:** `cd infra/terraform && terraform plan/apply` (tfvars·tfstate는 gitignore). 전체 삭제 = `terraform destroy`.
- **비밀 위치:** 카카오/AWS/서버 키는 전부 `.local/`·`~/.aws`·env로만. 레포 커밋 금지(규칙 D). 커밋 전 유출 스캔 필수.
- **비용:** ALB ~$16/월 + Fargate(0.25vCPU/1GB) ~$12/월. RDS·ECS·SSM·ECR 사실상 무료. $200 크레딧 + 예산 알림(실지출 $0.01↑ 메일).

## 다음 할 일 (우선순위)

### P2 · UGC + 인증 (백엔드 본론 다음 단계)
- [ ] **소셜 로그인**: 카카오/구글 OAuth2 + JWT 세션. (Boot 4 `spring-boot-starter-oauth2-client`/`-resource-server`, jjwt. mp에 레퍼런스 있음.)
- [ ] **제보(report)**: `POST /reports` 휘발성 상태(`expires_at`). 익명 허용, 로그인 시 신뢰도 가중. report_type enum은 CLAUDE.md §8.
- [ ] **후기(review)**: `POST /reviews`·`GET /places/{id}/reviews` 영구 평판(별점/코멘트/사진). 로그인 필요.
- [ ] **사진 업로드**: `POST /photos/presign` — **S3 버킷 + presigned URL**(terraform에 S3 추가 필요, 태스크 롤에 s3 권한).
- [ ] **신뢰도(trust_score)** 계산 + 제보 가중.
- [ ] **모더레이션 큐**: `POST /flags` 신고 + `GET /admin/flags/pending`(관리자).
- 프론트의 P2 예약 슬롯(최근 제보 타임라인·후기·로그인 배지)이 이걸 기다린다.

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
- [ ] 프론트 Vercel 배포 + Kakao **JavaScript 키**(REST 키와 다름, 콘솔 플랫폼>Web 도메인 등록) — 프론트 세션이 처리 중.

## 트러블슈팅 요약 (전부 `TROUBLESHOOTING.md`에 상세)
- **TS-001** SG description ASCII 전용 → 영어. (`terraform validate`는 API 값제약 못 잡음)
- **TS-002** `@Container`(static) × Spring 컨텍스트 캐시 수명주기 충돌 → 싱글턴 컨테이너.
- **TS-003** 빨간 CI 머지→크래시루프→ECS 런치 백오프 → 배포 서킷브레이커+롤백, 머지 게이트 exit code 판정.
- **TS-004** Boot 4(Jackson 3) ↔ 코드 Jackson 2 `JsonNode`로 지오코딩 전량 실패(0건) + 페이크 지오코더가 실 파싱 계약을 가린 사각지대 → record + MockRestServiceServer.
- **TS-005** 느린 부팅(93s, 0.25vCPU) > 헬스체크 유예(120s) → 서킷브레이커 오롤백 → 유예 240초.
> 공통 교훈: ① "Docker 있는 CI에서만 드러나는 실패"는 로컬 green→CI red → 반드시 CI green 확인 후 머지. ② 모킹은 "우리 로직"은 검증하되 "외부와의 실제 계약"을 가린다.

## 워크플로우 리마인더 (CLAUDE.md 필수 규칙)
- **커밋 신원** `hoengj`/`seongjuice999@gmail.com` (레포 config 고정, 커밋 전 확인).
- **의사결정**은 웹으로 2026 트렌드 확인 + `WORKLOG.md`에 무엇/왜/대안/근거 기록. 사고는 `TROUBLESHOOTING.md`.
- **기능 브랜치 + PR + CI green 확인 후 머지**(`gh run watch --exit-status`의 실제 exit code로 판정 — 파이프 tail 오판 금지).
- **비밀은 대화 OK, git 금지**(규칙 D). `.local/`·`.env`·tfvars/tfstate gitignore. 커밋 전 스캔.
- **범위 임의 확장 금지** — 새 기능은 제안 후 확인. 간판(지리공간)이 주인공, 커뮤니티는 살.

## 동시 작업 주의 (2026-07-03 현재)
프론트 세션이 같은 레포 `feat/frontend-mvp` 브랜치에서 작업 중. 백엔드 작업은 **backend/·infra/·docs/ 위주 + 자체 기능 브랜치**로 하면 프론트와 안 겹친다. `git add -A` 대신 **특정 파일만 add**해 상대 세션의 미커밋 작업을 삼키지 말 것.
