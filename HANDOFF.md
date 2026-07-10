# 그늘(Geuneul) — 프로젝트 현황과 다음 작업

> 현재 상태·완료분·다음 작업을 정리한 진행 문서. 전체 스펙·규칙은 [`CLAUDE.md`](./CLAUDE.md), 의사결정은 [`docs/adr/`](./docs/adr), 일지는 [`WORKLOG.md`](./WORKLOG.md), 사고기록은 [`TROUBLESHOOTING.md`](./TROUBLESHOOTING.md).
> 최종 갱신: 2026-07-10.

## ▶ 세션 인계 — 다음 세션은 여기서 시작
- **상태(2026-07-10 상권-적재 세션)**: 라이브 정상(App·API, 태스크데프 **rev49**). **상권정보 카페/스터디카페(B553077) 계약 검증 + 코드기반 서버필터 + 격자 적재 완료·라이브(PR #56, TS-026).** data.go.kr 승인 확인(`resultCode 00`) 후 미검증 스캐폴드 실측 정정(응답 래퍼 없음·숫자 좌표·업종코드 I21201/R10202), 프로덕션 실적재(`/places?category=CAFE|STUDY_CAFE` 실측 200·study_ok 백필 확인). **남은 외부 블로커 = 쉼터 전국(safetydata) 1건뿐** — data.go.kr에서 폐기, safetydata.go.kr 별도 포털·별도 키 필요(아래 ⏳). 상세는 WORKLOG 2026-07-10 상권 항목·아래 백로그 섹션.
- **직전 상태(2026-07-10 P4-백로그 세션)**: 외부 승인 불필요 백로그 ①~⑨ 전부 완료·배포·프로덕션 실측(PR #44~#53, 핫픽스 #52). Flyway **V9~V12** 프로덕션 무사 적용(급증 트리거·verified·커뮤니티·모더레이션 hidden). 새 라이브 엔드포인트 실측 200: `/alerts/surge`·`/alerts/stream`(SSE)·`/places/{id}/popular-times`·`/recommendations?scenario=focus|longstay`·상세 `features`/`aiSummary`·`/admin/flags?status=`·`/reviews/{id}/comments`·`/reactions`. JaCoCo floor 0.60→**0.70**.
  - ⚠️ **프로세스 교훈(TS-025)**: 이 세션 초반 ⑥·⑦·⑧을 CI red 상태로 머지함(`gh run watch` EXIT=0을 오신뢰 — 실패 테스트 1건 CommunityFlowIT 프로젝션 버그). #52로 복구. **이후로 머지 전 `gh pr checks <N>`로 "Backend (Gradle, JDK 21)" pass를 반드시 눈으로 확인.**
  - **커밋 규칙 신설(CLAUDE.md §A)**: `Co-Authored-By: Claude` 트레일러 금지(GitHub contributors에 ghdtjdwn만). 과거 39개 커밋은 이 세션에 history rewrite로 제거·force-push 완료(트리 불변, author 전부 유저).
- **직전 세션 상태(로드맵 P2~P4 완주)**: 라이브 태스크데프 **rev41**(전 secret 보존: DB·proxy·KMA·kakao×2·google×2·jwt·**ai_summary·datago**·S3 env). Flyway **V5~V8** 프로덕션 무사 적용. **ECS 오토스케일링 라이브**(min1/max3, CPU60% target-tracking). **AI 한줄요약 라이브(#41, Mistral) — aiSummary 비-null 실측 완료.** **무료 HTTPS 라이브(#43, CloudFront 기본 도메인 `https://d2pedv974beobb.cloudfront.net`, ADR-0015).** **주기동기화 스케줄 ENABLED(#42, 실트리거 검증 후).** **화장실 좌표 52,334건(#42 세션 재적재로 46,897→+5,437).**
- **이번 세션 라이브(11 PR, 전부 실측 검증)**: #30 날씨 comfort(ADR-0009) · #31 후기(review) · #32 공부공간 데이터확장(V5, ADR-0006) · #34 trust_score 실배선(V6) · #33 모더레이션 flags(V7) · #35 S3 사진 presign(버킷 geuneul-photos-691684280989) · #36 AI 한줄요약(ADR-0010; 이후 #41서 Mistral 전환) · #37 주기동기화(EventBridge→RunTask, ADR-0011; 이후 #42서 ENABLED) · #38 k6+EXPLAIN+V8 인덱스튜닝(ADR-0012) · #39 ECS 오토스케일링(ADR-0013) · #40 관측성 OTel/Micrometer(ADR-0014). 실측: `/weather`·`/flags` 401·`/photos/presign` S3 URL·`/places`·`/recommendations` 전부 정상.
- **인프라 신규(이번 세션)**: terraform apply — S3 버킷+IAM(7) · SSM openrouter(→이후 ai_summary로 rename, #41)/datago + EventBridge 스케줄(당시 DISABLED→#42서 ENABLED)+IAM(5) · 오토스케일링 target+policy(2). (후속 세션: CloudFront 배포 1건 추가, #43.) 태스크데프 수동 rev 여러 번(secret 누적). ⚠️교훈: 라이브 rev 조립 시 **describe 대상이 최신 이미지 rev인지 확인**(rev33이 구 이미지로 조립돼 presign 404 → 재조립). EventBridge Universal Target input은 **PascalCase** 필수(TS-020).
- **AI(곁다리) 상태 — 라이브(2026-07-10, PR #41)**: 프로바이더를 **OpenRouter → Mistral로 전환**하고 데모 출력을 살렸다. 클라이언트가 원래 OpenAI 호환 범용(`/chat/completions`, base-url·key·model 전부 설정값)이라 **코드 로직 변경 0**으로 config만 교체 — 겸사겸사 이름-실체 불일치를 없애려 `OpenRouterClient→ChatCompletionClient`·`ai.openrouter.*→ai.summary.*`·env `OPENROUTER_*→AI_SUMMARY_*`·SSM `openrouter_api_key→ai_summary_api_key`로 **프로바이더 중립 리네임**(ADR-0010 §4 갱신). 실측: `GET /places/185` → `aiSummary="시원하다는 제보가 최근에 있습니다."`(Mistral 생성, 비-null). 프로바이더 선정 경위: OpenRouter 429(계정단위)·Groq 키무효·Gemini 429·DeepInfra 잔액부족 → **Mistral·SambaNova만 생존**, Mistral이 침수 순화 품질 우위로 채택(`mistral-small-latest`, 무료티어). 프로바이더 교체는 `AI_SUMMARY_BASE_URL/MODEL` env + SSM 키만 바꾸면 끝(코드변경 0). 키체인 `.local/ai.env`(SambaNova가 백업).
- **보안 수정(TS-022)**: `/actuator/prometheus`가 프로덕션에 **인증 없이 공개**돼 있던 것(정보노출) 발견 → `MANAGEMENT_EXPOSURE:health,info` 기본으로 닫음(#40 배포 반영). 로컬 관측성 스택 `docker-compose --profile observability`(Prometheus+Grafana+Tempo).
- **미착수/후속 — 남은 "사용자 콘솔 승인 대기" 블로커 1개**: ① **쉼터 전국(safetydata)** — 무더위쉼터 표준데이터가 **data.go.kr에서 폐기·이관**(실측 `resultCode 12`/미등록), 실데이터는 **safetydata.go.kr `DSSP-IF-10942`** 에만 존재하며 **별도 포털·별도 서비스키** 필요(datago 키로 `resultCode 30`). 풀려면 사용자가 safetydata.go.kr 가입→활용신청 승인→전용키 발급(상세 아래 ⏳ 섹션). (~~② 상권정보 STUDY_CAFE/CAFE~~ — **완료 PR #56**, data.go.kr 승인 확인·계약검증·격자 적재. · ~~EventBridge 스케줄~~ #42 · ~~ALB HTTPS~~ #43 · ~~화장실 재적재~~ +5,437 · ~~AI 유효키~~ #41 — 전부 완료.)
- **P3 날씨 라이브(PR #26 + 핫픽스 #28·#29)**: `GET /weather?lat=&lng=` — 기상청 초단기실황(getUltraSrtNcst) + 격자변환 + **ElastiCache Redis TTL 캐시(30분) 실동작**. 프로덕션 실측: 광화문 `지금 23°C, 비`, 부산 `31°C`, 캐시 히트 정상. serviceKey는 `.local/datago.env`(data.go.kr 계정 공통 키). **하드닝: 두 캐시 버그 배포 중 발견·수정 — TS-011(@Cacheable Optional 언랩 SpEL), TS-012(무타이핑 직렬화 캐시히트 500). 회귀 테스트 2건 추가.**
- **P2 소셜 로그인 라이브·검증 완료(PR #27)**: 카카오/구글 OAuth2(BFF code 서버교환) + JWT + `/me` + 프론트 "내 정보" 탭. **브라우저 실사용 성공 — 구글·카카오 둘 다 프로필까지 표시**(홍성주/카카오·구글). 카카오는 콘솔 설정 3건으로 지연됐다(TS-013): Redirect URI를 로그아웃 칸에 잘못 등록·호출허용IP 127.0.0.1·Client Secret ON 배선. 키는 `.local/oauth.env`·SSM·Vercel env(KAKAO_REST_API_KEY·GOOGLE_CLIENT_ID).
- **인프라 신규**: ElastiCache Redis(`cache.t3.micro`, 프리티어 대상) + SSM 6종(kma/kakao_rest/kakao_secret/google×2/jwt). `elasticache.tf`·`ssm.tf`·`ecs.tf`. **참고: Redis를 지워도 CacheErrorHandler로 날씨는 기상청 직접호출로 계속 동작(무료화 시 코드변경 0).**
- **콘솔 없이 바로 가능한 다음** — ✅ **전부 완료**(~~A~D 로드맵~~ / ~~AI 데모 출력(#41 Mistral)~~ / ~~EventBridge 스케줄 활성화(#42)~~ / ~~화장실 실패 재적재(#42, +5,437)~~ / ~~ALB HTTPS(#43 CloudFront, ADR-0015)~~). **남은 건 위 '미착수/후속'의 사용자 콘솔 승인 대기 2건(쉼터 전국·상권정보)뿐.**
  - **HTTPS 링크(신규)**: `https://d2pedv974beobb.cloudfront.net`(health·swagger·API, http→301). ALB(http)는 CloudFront 오리진으로 유지, 프론트 BFF는 서버사이드로 ALB 직접(무영향, ADR-0004).
  - **도메인 확보 시(선택)**: CloudFront에 CNAME+ACM(us-east-1) 붙이거나 ALB 443 리스너로 전환 — ADR-0015 대안표.
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

## 지금 상태 — 로드맵 P1~P4(A~D) 완주 · 프론트 MVP 라이브 · 간판(지리검색+UGC 스코어링) 완성

🟢 **API Live (HTTPS):** https://d2pedv974beobb.cloudfront.net (`/actuator/health`, `/swagger-ui.html`) — CloudFront 무료 HTTPS(ADR-0015). http ALB(`http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com`)는 오리진으로 유지.
🟢 **App Live:** https://geuneul.vercel.app (프론트 PWA — Kakao 실지도 + 라이브 데이터 + 실시간 제보)

- **백엔드:** Spring Boot 4.0.6 / Java 21. 반경(`ST_DWithin` geography)·최근접(kNN `<->`)·bounds 공간검색 API 라이브. `backend/`.
- **인프라:** AWS ECS Fargate(+**오토스케일링** min1/max3 CPU60%) + RDS PostgreSQL(PostGIS) + **ElastiCache Redis** + **S3**(사진) + **EventBridge Scheduler**(주기동기화, **ENABLED** 월1회) + **CloudFront**(무료 HTTPS 기본 도메인, ADR-0015) + Terraform(IaC) + GitHub Actions OIDC + ECR + ALB. `main`에 `backend/**` push 시 자동배포. **현재 라이브 태스크데프 rev49**(REDIS_HOST·S3_BUCKET_NAME·AWS_REGION·**AI_SUMMARY_BASE_URL·AI_SUMMARY_MODEL** env + SSM secret **10종**=DB·PROXY·KMA·KAKAO_REST·KAKAO_SECRET·GOOGLE_ID·GOOGLE_SECRET·JWT·**AI_SUMMARY·DATA_GO_KR**). 라이브 태스크데프는 `ignore_changes`라 secret/env 추가 시 **수동 rev 등록**(describe→추가→register→update-service). `infra/`.
- **데이터(프로덕션 RDS):** 무더위쉼터 100건(전국 샘플 — 전국 적재는 safetydata 승인 대기, ⏳) + 공중화장실 **52,334건**(카카오 지오코딩 — 2026-07-10 실패 7,193 재시도로 46,897→+5,437, 잔여 1,756은 카카오가 못 푸는 옛 지번·산번지) + 도서관(전국 3,551건, library API 멱등) + **상권 카페 26,726 · 스터디카페 3,160(서울 전역, 2026-07-10 PR #56 격자 적재, 전량 WGS84 내장·지오코딩 0)**. 총 ~85,900곳. 광화문·대전·부산·강릉·창원·강남·종로·노량진 라이브 검증 통과. (전국 확장은 `prod-ingest-stores.sh`에 더 큰 bbox — 멱등.)
- **P2 제보(라이브, PR #15·#16·#17):** 익명 휘발성 제보 `POST /reports`(타입별 TTL로 `expires_at`) + `GET /places/{id}/reports`. 프론트 제보하기 실전송(장소=nearest+피커)·상세 "최근 제보" 실시간. 인메모리 레이트리밋(분3·시간10) — XFF 신뢰경계(`ProxyClientResolver`)·OOM 하드닝(TS-008).
- **P3 survival_score(라이브, PR #23·ADR-0007):** `place_report_signals` 뷰(V4)가 유효제보를 최근성×신뢰도로 집계(freshness/comfort/risk) → 순수 함수 `SurvivalScore`가 §5 가중치 조립·등급(GOOD/OKAY/UNKNOWN). `/places`(반경·bounds)·`/places/{id}` 응답에 `survival` 필드. 프론트 마커 3색 링·리스트/상세 상태 배지. open_now는 운영시간 결측이라 재정규화 제외(데이터 붙으면 복원), 후기는 §5대로 분리. **로컬 검증: postgis에 V1~V4 직접 적용 + 시나리오별 뷰/쿼리 실행으로 시맨틱 확증(TS-009), 엔드투엔드 IT는 CI.**
- **P3 추천(라이브, PR #24·ADR-0008):** `GET /recommendations?scenario=rest30|restroom|rain` — survival_score 순수 함수를 시나리오 가중치로 재사용(`SurvivalScore.Weights` 오버로드) + 2단 검색(공간 인덱스 선필터 `findWithinRadiusScoredByCategories` → 앱 재랭킹). 응답=canonical `survival` 배지 + `matchScore`(적합도, 정렬 기준) + `reason`(제보 요약). 가중치: rest30 comfort↑(0.35) / restroom distance 압도(0.60) / rain risk↑(0.40, 침수 강등 — 배지는 §6대로 −0.15 순화 유지). 프론트 "급해요"를 nearest 팬아웃 → 정식 랭킹으로 승격(`/api/urgent`가 `/recommendations` 프록시). **검증: 단위 15건 green + 엔드투엔드 IT 3건은 colima 이슈로 로컬 skip→CI green 후 머지·배포. 프로덕션 실측(광화문 화장실 거리순 matchScore, 상도동 rest30에서 제보 있는 도서관이 무제보 쉼터 앞섬).**
- **프론트(완료, PR #12·#14·#16):** Next.js 16(App Router)+TS PWA — MVP 4화면(홈 지도·장소 상세(실지도 미니맵·최근제보)·급해요·제보 실전송). **동일 오리진 서버 프록시(BFF)** 로 ALB(http)·CORS 회피(ADR-0004) → **백엔드 CORS 불필요**. Vercel git 연결로 `main` push 시 자동배포(rootDirectory=frontend). Kakao JS 키는 콘솔 **[JavaScript 키 > JavaScript SDK 도메인]** 에 `https://geuneul.vercel.app` 등록 완료(제품링크관리>웹도메인과 다른 칸 — 혼동 주의).
- **테스트:** 파서/컨트롤러/지오코딩/스코어/AI/모더레이션 단위 + 실 PostGIS IT(멱등·공간쿼리·뷰·플래그·트러스트). **JaCoCo floor 0.60 ratchet**(#32에서 0.35→0.60, 실측 65.7%). CI(`ci.yml`)가 실 PostGIS로 검증. 프론트는 `frontend-ci.yml`(typecheck·lint·build). **로컬 IT는 colima 이슈로 skip(TS-009) — CI가 유일한 실 게이트, SKIP≠통과.**

## 아키텍처 한눈에
```
backend/    Spring Boot 4 — domain.place(공간검색·survival) · domain.report(제보) · domain.review(후기) · domain.flag(모더레이션) · domain.photo(presign) · domain.recommend(추천) · domain.weather(날씨) · domain.ai(요약) · domain.auth(OAuth·JWT·trust) · domain.ingest(+geocode·openapi·storeapi·batchlock) · global.geo/config/security
infra/      terraform/(VPC·RDS·ECS+오토스케일링·ALB·ECR·IAM OIDC·ElastiCache·S3·SSM·EventBridge scheduler) · scripts/prod-ingest.sh
frontend/   Next.js 16 App Router PWA — app/(shell)(홈지도·상세·급해요·제보·후기·내정보) · app/api(서버 프록시 BFF) · components · lib
observability/  로컬 관측성 스택(Prometheus·Grafana·Tempo, docker-compose --profile observability)
perf/       k6 부하테스트(spatial_load.js)·seed·EXPLAIN RESULTS(P4)
docs/       adr/0001~0014 · design-brief.md
.github/    ci.yml(백엔드 테스트) · deploy.yml(OIDC 배포, paths=backend/**) · frontend-ci.yml(paths=frontend/**)
.local/     (gitignore) myInfo·PORTFOLIO-CONTEXT — 비밀·회사매핑
```

## 운영 치트시트
- **배포:** `main`에 `backend/**` 머지 → 자동. 문서·인프라만 바뀐 push는 배포 트리거 안 됨(paths 필터).
- **공공데이터 재적재(멱등):** `KAKAO_REST_API_KEY=<키> ./infra/scripts/prod-ingest.sh public_toilet <릴리즈URL> MS949` (쉼터는 키 불필요·UTF-8). 데이터 스냅샷은 GitHub Release `data-v1`.
- **인프라 변경:** `cd infra/terraform && terraform plan/apply` (tfvars·tfstate는 gitignore). 전체 삭제 = `terraform destroy`.
- **비밀 위치:** 카카오/AWS/서버 키는 전부 `.local/`·`~/.aws`·env로만. 레포 커밋 금지(규칙 D). 커밋 전 유출 스캔 필수.
- **비용:** ALB ~$16/월 + Fargate(0.25vCPU/1GB) ~$12/월. RDS·ECS·SSM·ECR 사실상 무료. 오토스케일링 스케일아웃 시 최대 ~$36/월(부하 종료 600초 후 원복). $200 크레딧 + 예산 알림(실지출 $0.01↑ 메일).
- **AI 요약 프로바이더 교체(라이브=Mistral rev41, 복붙):** 클라이언트가 OpenAI 호환 범용이라 프로바이더 교체는 코드변경 0 — SSM 키 + `AI_SUMMARY_BASE_URL/MODEL` env만 바꾼다.
  ```bash
  # 키만 회전: AI_SUMMARY_API_KEY secret은 rev41+에 배선됨 → SSM 값만 갱신 후 강제 재배포
  aws ssm put-parameter --name /geuneul/ai_summary_api_key --type SecureString --overwrite --value '<유효 키>'
  aws ecs update-service --cluster geuneul --service geuneul --force-new-deployment
  # 프로바이더/모델까지 바꾸려면 태스크데프 rev의 AI_SUMMARY_BASE_URL·AI_SUMMARY_MODEL env 변경(describe→register→update-service)
  # 검증: 제보 있는 place 상세 GET(예: /places/185) → aiSummary 비-null. 앱 로그(/ecs/geuneul)는 실패 시에만 "[ai]" warn(성공은 무로그)
  ```
- **주기동기화 스케줄 켜기:** `cd infra/terraform && terraform apply -var ingest_schedule_enabled=true`(실트리거 1회 검증 후). 스케줄명 `geuneul-public-data-sync`, Universal Target `aws-sdk:ecs:runTask`(input PascalCase, TS-020).

## 다음 할 일 (우선순위)

### ✅ 완료·라이브 (로드맵 P2~P4 — 2026-07-10 세션 반영)
- **P2 UGC+인증**: 제보(#15~17) · 소셜로그인 카카오/구글 OAuth+JWT(#27) · 후기 review(#31) · 사진 presign S3(#35) · trust_score 실배선(#34, V6) · 모더레이션 flags+ADMIN 큐(#33, V7) · 레이트리밋 proxy-secret(TS-008). **프론트 예약슬롯 중 후기·사진 업로드까지 라이브.**
- **P3 스코어·추천·AI·데이터**: survival_score(#23, ADR-0007) · 추천(#24, ADR-0008) · 날씨+Redis캐시(#26) · 날씨 comfort 복원(#30, ADR-0009) · **AI 한줄요약 라이브(#36 배선→#41 Mistral 전환·중립 리네임, ADR-0010 §4 — aiSummary 비-null 실측 완료)** · 공부공간 데이터확장(#32, V5, ADR-0006) · **주기동기화 EventBridge→RunTask(#37 스캐폴드→#42 ENABLED, ADR-0011 — 실트리거 검증 후 활성화)**.
- **P4 심화(간판)**: k6+EXPLAIN+V8 인덱스튜닝(#38, ADR-0012) · ECS 오토스케일링(#39, ADR-0013, apply 완료) · 관측성 OTel/Micrometer(#40, ADR-0014, `/actuator/prometheus` 노출 닫음 실측 404) · **무료 HTTPS CloudFront(#43, ADR-0015 — `https://d2pedv974beobb.cloudfront.net` 실측).**

### ✅ 최근 완료 (2026-07-10 후속 세션 — 외부 스위치 4건)
- [x] ~~AI 데모 출력 살리기~~ — **#41 Mistral 전환·프로바이더 중립 리네임, aiSummary 비-null 라이브.**
- [x] ~~EventBridge 스케줄 활성화~~ — **#42 실트리거 검증(exit 0) 후 ENABLED, default=true 승격.**
- [x] ~~화장실 실패 7,193건 재시도~~ — **#42 재적재로 5,437건 회복(46,897→52,334), 잔여 1,756은 데이터 한계.**
- [x] ~~ALB HTTPS~~ — **#43 CloudFront 기본 도메인(ADR-0015), `https://d2pedv974beobb.cloudfront.net` 실측.**

### ⏳ 외부 승인 대기 (다음 세션 대상 아님 — 사용자 콘솔 승인 후에만 가능)
- [ ] **쉼터 전국(safetydata) — 유일한 잔여 외부 블로커.** 무더위쉼터 표준데이터는 **data.go.kr에서 폐기·이관**됐다(2026-07-10 실측: 후보 엔드포인트 전부 `resultCode 12`/`-3`/미등록). 실데이터는 **재난안전데이터공유플랫폼 `safetydata.go.kr`** 의 `DSSP-IF-10942`(무더위쉼터)에만 있고, **safetydata는 data.go.kr과 완전히 별개 포털·별개 서비스키**다 → 현재 datago 키로 호출하면 `resultCode 30 (등록되지 않은 서비스키)`. **해금 절차(사용자 필요)**: ① `safetydata.go.kr` 회원가입/로그인 → ② `DSSP-IF-10942` 오픈API **활용신청 승인** → ③ 발급된 **safetydata 전용 인증키**를 `.local/`(예: `safetydata.env`)에 저장. 키 확보되면 → safetydata API 클라이언트를 **새 소스로** 붙여(library 멱등 패턴 재사용) 전국 쉼터 적재. 엔드포인트 `https://www.safetydata.go.kr/V2/api/DSSP-IF-10942`.
- [x] ~~**상권정보 카페/스터디카페(B553077)**~~ — **완료·라이브(2026-07-10, PR #56, TS-026).** data.go.kr 활용신청 **승인 확인**(`resultCode 00`) → 미검증 스캐폴드를 실측 정정: 응답 `{header,body}` 최상위(래퍼 없음)·좌표 JSON 숫자, 업종코드 **카페 I21201·독서실/스터디카페 R10202** 확정, `indsSclsCd` 서버측 필터 + `ingestArea` 격자 순회. 프로덕션 실적재(서울 전역 340셀, distinct CAFE 26,726·STUDY_CAFE 3,160, geocoded 0). 엔드포인트 `https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInRadius`. 재적재: `KAKAO_REST_API_KEY=<키>(선택) ./infra/scripts/prod-ingest-stores.sh <minLng,minLat,maxLng,maxLat> [radius]`.

### ✅ 외부 승인 불필요 백로그 — **2026-07-10 세션에 ①~⑨ 전부 완료·라이브** (PR #44~#53)
> 규율 준수: 간판 우선, 커뮤니티는 살로만(프론트 UI 최소). 각 항목 기능 브랜치 + PR + CI green + 프로덕션 실측.

**간판 강화 (로드맵 P4 잔여):**
- [x] ~~**① 실시간 제보 급증 알림**~~ — **완료(#44, ADR-0016).** Postgres LISTEN/NOTIFY→SSE(멀티 인스턴스 팬아웃, Kafka/Redis Streams 배제). `GET /alerts/surge?bounds=`(스냅샷)·`GET /alerts/stream`(SSE). V9 트리거. 라이브 실측 200.
- [x] ~~**② 시간대별 혼잡 파생(자체 popular-times)**~~ — **완료(#45).** `GET /places/{id}/popular-times` KST 요일×시간 집계(만료 포함=이력 채굴). #53에서 Redis 1h 캐시 추가.
- [x] ~~**③ GPS 방문 인증**~~ — **완료(#46, V10).** `reports.verified` — 제보자 좌표 `ST_DWithin(100m)` → place_report_signals 뷰에서 ×1.3 가중(비검증 불변, 단조).

**살·즉효:**
- [x] ~~**④ place_features.value 등급화**~~ — **완료(#47).** `FeatureGrade`(콘센트 many/some/few·wifi·noise_level 신설) → 상세 `features` 등급 칩. 스키마 변경 0. survival 수치 재배선은 후속(간판 마커 불흔들, §9).
- [x] ~~**⑤ 추천 시나리오 focus/longstay**~~ — **완료(#48).** enum+Weights만(오버로드 재사용). 라이브 실측 200.

**2차 (살로만 — 프론트 UI 미노출):**
- [x] ~~**⑥ 후기 커뮤니티**~~ — **완료(#49, V11).** `review_comments`·`reactions`(다형·멱등). survival_score 무연결(§0-9). 프론트 UI 최소(미노출).
- [x] ~~**⑦ 모더레이션 확장**~~ — **완료(#50, V12).** 신고 RESOLVED→대상 hidden(공개·스코어·급증·혼잡·AI·후기 6경로 제외), DISMISSED 유지, `GET /admin/flags?status=` 이력.

**품질·인프라:**
- [x] ~~**⑧ 프론트**~~ — **완료(#51).** CAFE/STUDY_CAFE 필터·place_features 등급 칩·verified 배지·focus/longstay·AI요약 라이브(BFF 패스스루). 커뮤니티 UI는 최소(§9).
- [x] ~~**⑨ JaCoCo 상향·캐시 심화**~~ — **완료(#53).** floor 0.60→**0.70**(실측 71%)·popular-times Redis 1h 캐시(타입 바인드 직렬화, TS-011/012 회피).
- [x] **핫픽스 #52(TS-025)**: ⑥ 후기 댓글 프로젝션이 timestamptz를 `OffsetDateTime`으로 받아 CI 500(TS-016 재발) → `Instant`+UTC 부착. **교훈: 머지 전 반드시 `gh pr checks`로 백엔드 게이트 pass 확인**(`gh run watch` EXIT만 믿지 말 것).
- [ ] 트래픽 붙으면 **Fargate task_cpu 512**(부팅 93초 단축, TS-005 근본 해결). — 여전히 미착수(트래픽 대기).

**다음 세션 남은 것 = 외부 승인 대기 2건(위 ⏳ 쉼터 safetydata·상권 B553077)뿐 + 후속 additive(급증 SSE 프론트 구독·popular-times 히트맵·커뮤니티 최소 UI·verified→유저 trust 연동·시설 comfort SQL 통합).**

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
