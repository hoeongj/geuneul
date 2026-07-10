# 그늘 (Geuneul) — 작업 로그 (WORKLOG)

> **목적:** 주요 기술 결정의 배경(**무엇을 / 왜(why) / 검토한 대안 / 트렌드 근거**)을 시간순으로 남기는 개발 일지.
> 언제 무슨 작업을 했는지 + **스펙·방법·로직·알고리즘·라이브러리를 왜 그렇게 골랐는지(why)** 를 남긴다.

## 작성 규칙
- **작업할 때마다 맨 아래에 append.** 덮어쓰지 말고 시간순으로 계속 쌓는다.
- 결정(스펙/라이브러리/알고리즘/구조)을 내릴 때마다: **선택 · 이유 · 검토한 대안 · 트렌드 근거(웹검색 결과)** 를 반드시 기록.
- 판단 순서 = 프로젝트 적합성 → 2026 트렌드/베스트프랙티스 부합 → 근거로 방어 가능한 선택인가.
- 날짜는 `YYYY-MM-DD` 형식. 커밋 해시/PR이 있으면 함께 남긴다.

## 엔트리 템플릿
```
### YYYY-MM-DD — <한 줄 요약>
- 한 일:
- 결정 & 이유(why): <무엇을 골랐고, 왜. 검토한 대안 A/B, 트렌드 근거>
- 관련 파일/커밋:
- 다음 할 일:
```

---

## 로그

### 2026-07-01 — 프로젝트 기획 확정 및 레포 세팅
- 한 일: 지도 프로젝트 기획서 검토 → **재고맵(헛걸음맵) 드롭**, **그늘 단일 플래그십으로 확정**. 폴더/스펙(`CLAUDE.md`)·git 신원·로그 문서 세팅.
- 결정 & 이유(why):
  - **재고맵 제외** — 저작권법 제93조(DB제작자 권리) + 브랜드 비공식 API 리버싱 리스크 + 재고 정확도 낮아 "헛걸음 방지"가 헛걸음을 유발하는 역설. 포트폴리오 대비 리스크가 커서 제외.
  - **그늘 1순위** — 여름 시즌(7월)이라 실사용 테스트가 바로 가능, 공공데이터·PostGIS·AI 요약 조합이 백엔드 포트폴리오로 가장 강함.
  - **스택**: Spring Boot 3/Java 21 + PostgreSQL/PostGIS + Next.js(PWA) + Kakao Maps + Claude API. 이유 = 백엔드 취업 포지션(Java) 정렬 + 지도 반경검색은 PostGIS가 사실상 표준.
- 관련 파일/커밋: `CLAUDE.md`, `README.md`, `WORKLOG.md`, `TROUBLESHOOTING.md`
- 다음 할 일: W0 — Docker Compose(PostGIS+Redis), DB 스키마, Kakao Maps SDK 연결.

### 2026-07-02 — 스펙 전면 개정: 맛핀 흡수, 지리공간 심화 단일 플래그십으로 재정의
- 한 일: 기존 포트폴리오(mp=숭실대 AI 생태계 ~84k LOC 프로덕션 / dom=기숙사매칭 / ddsc=AI채점 VN) 분석 → 그늘의 포지셔닝을 "기존이 안 가진 축"으로 재정의. 형제 지도앱 **맛핀 삭제·그늘로 흡수**. CLAUDE.md 전면 개정(스택 Boot4, ERD/API/로드맵 확장, 워크플로우 규칙 D 추가).
- 결정 & 이유(why):
  - **그늘 = 지리공간 + 실시간 UGC 쇼케이스 (간판)** — 기존 포트폴리오가 MCP/RAG/에이전트/분산/K8s는 넘치게 증명했으나 **지리공간(PostGIS)·대용량 공공데이터 ETL·실시간 UGC 스코어링은 공백**. 그늘을 그 갭에 정확히 맞춤. AI 요약은 곁다리로 강등.
  - **맛핀 흡수(별도 앱 폐기)** — 맛핀도 지도앱이라 스택 100% 겹침 → "지도앱 2개"는 오히려 약함. 맛핀의 로그인/후기/커뮤니티/신뢰도만 그늘에 병합, **친구/그룹 그래프·스토리·Match Score는 제외**(그늘은 폐쇄 친구망이 아니라 공개 커먼스). 대안: (A)둘 다 유지 → 중복·분산, (B)공통 geo-core 공유 두 제품 → 스코프 과다. → 단일 심화가 최선.
  - **UGC 2단 설계(제보 휘발 vs 후기 영구)** — 제보는 freshness로 survival_score 구동, 후기는 장소 평판으로 분리. 맛핀 "스토리"의 낮은 부담 입력 역할은 그늘에선 제보가 이미 수행 → 스토리 불필요.
  - **스택 Spring Boot 4 / Java 21** — 대안 Boot 3(기존 CLAUDE.md). 본인 mp가 Boot 4라 Boot 3는 구식 인상 → Boot 4로 통일. 트렌드 근거: mp 생태계 정렬 + 2026 현재 GA.
  - **지리 스택 = Hibernate Spatial 6.6 + JTS 1.19 + PostgisDialect, `geometry(Point,4326)`; 반경 `ST_DWithin`(GiST 자동 인덱스), kNN `<->`+ORDER BY** — 웹검증(2026): ST_DWithin은 GiST 인덱스 필터 자동 적용, `<->`는 PG9.5+ 진짜 KNN, 거리/KNN엔 GiST가 최적(2025 벤치 GiST>SP-GiST). 대안 애플리케이션 레벨 거리계산 → 전체스캔이라 기각.
  - **지오코딩 = 카카오 로컬 API(주소→좌표)** — 공중화장실 표준데이터 WGS84 결측 보완. 이미 Kakao Maps 사용 → 정렬. 결과 좌표 저장으로 멱등·rate limit 회피. 대안: VWorld·국토부 지오코더(백업).
  - **인프라 = 기존 Oracle Cloud k3s + ArgoCD 재사용** — 신규 구축 대비 일 적고 생태계 일관성. 이벤트는 Redis Streams/LISTEN·NOTIFY(Kafka 과설계 금지).
  - **타깃 회사/직무 미확정 유지 + 스펙은 타깃 비종속** — 당근·배민·토스 JD 교집합(Spring Boot+PostgreSQL/JPA+테스트+캐시+대용량)이 그늘 코어와 1:1이라, 특정 회사에 안 박아도 범용으로 강함. 지리공간은 위치회사엔 직결·타 회사엔 중립~플러스. 회사 매핑은 내부노트(.local)로만.
  - **워크플로우 규칙 D 추가(개인정보·비밀)** — 대화 중 개인정보 공유는 허용, GitHub 커밋/푸시는 금지. `.gitignore`(.local/·.env) 세팅, 유출 스캔 필수.
- 트렌드 근거(웹검색): Crunchy Data(PostGIS KNN/인덱스), PostGIS 공식 문서(ST_DWithin·`<->`), Baeldung(Hibernate Spatial), Kakao Local API, 당근 백엔드(Java) 공고(Java/Kotlin+Spring Boot+PostgreSQL 명시).
- 관련 파일/커밋: `CLAUDE.md`(전면 개정), `.gitignore`(신규), `.local/`(myInfo·PORTFOLIO-CONTEXT), `WORKLOG.md`
- 다음 할 일: W0 — Docker Compose(PostGIS+Redis) + Flyway 스키마 + Kakao Maps SDK + CI 스켈레톤. (API 응답 DTO 확정 시점에 claude design 착수 알림.)

### 2026-07-02 — W0: 백엔드 스캐폴드 + 로컬 인프라(PostGIS/Redis) + CI (빌드 검증까지)
- 한 일: `backend/` Spring Boot 4 프로젝트 생성(Gradle), PostGIS+Redis `docker-compose.yml`, Flyway `V1__enable_postgis`/`V2__create_core_tables`(users/places/place_features/reports/reviews + **geom GiST 인덱스**), Testcontainers 컨텍스트 테스트, GitHub Actions CI, `.env.example`. `./gradlew build`·`compileTestJava` **성공**(로컬 Docker 없어 통합테스트는 CI에서 실행 — self-skip 설계).
- 결정 & 이유(why):
  - **mp(ssuMCP) 검증 컨벤션 재사용** — Boot 4.0.6 / Java 21 / Gradle 9.5.1 래퍼, Flyway(`spring-boot-flyway`+core+postgresql), Testcontainers BOM 1.20.6, JaCoCo 게이트, 보안 오버라이드(tomcat 11.0.22·pgjdbc 42.7.11). 이유: 이미 프로덕션 검증 셋업 → 리스크↓·포트폴리오 일관성↑. (그늘용으로 MCP/Spring AI/rusaint/resilience4j는 제외해 경량화.)
  - **Hibernate Spatial + `geometry(Point,4326)` + GiST** — 반경(ST_DWithin)·kNN(`<->`)을 DB 인덱스로. 대안(앱 레벨 거리계산=전체스캔) 기각.
  - **Flyway = 스키마 단일 진실원천, JPA `ddl-auto=validate`** — 드리프트 방지·재현성. 자연키 `(source, source_external_id)` UNIQUE로 공공데이터 멱등 upsert 준비.
  - **Testcontainers self-skip(`disabledWithoutDocker`)** — 오프라인 로컬도 green, CI(Docker)에선 실 PostGIS로 Flyway·확장·인덱스까지 검증. H2가 놓치는 dialect 드리프트 포착.
  - **JaCoCo floor 0.00(W0)** — 로직 전이라 시작점. 도메인 붙으면 측정치 바로 아래로 ratchet 상향.
- 관련 파일: `backend/build.gradle`·`application.yml`·`db/migration/V1,V2`·`GeuneulApplicationTests.java`, `docker-compose.yml`, `.github/workflows/ci.yml`, `.env.example`
- 다음 할 일: (a) W0 프론트 — Next.js(PWA)+Kakao Maps SDK, 또는 (b) P1 — 공공데이터 idempotent 인제스천 + 반경/kNN 검색 API + JPA 엔티티.

### 2026-07-02 — CD 방향 재검토: mp GitOps 복제 대신 PaaS 경량 배포로 선회
- 한 일: GitHub public 레포 생성(hoeongj/geuneul) + push → **CI 실제 GREEN**(GitHub 러너에서 PostGIS/Redis 통합테스트·Flyway·GiST까지 통과, 1m33s). 이후 배포(CD) 방식을 포트폴리오 기준으로 재검토.
- 결정 & 이유(why):
  - **mp의 k3s+ArgoCD+Helm GitOps를 그늘에 복제하지 않기로.** 판단 근거(기준 1순위=포트폴리오): mp가 이미 자가호스팅 k8s/GitOps를 최고 수준으로 증명 → 그늘이 동일 반복 시 **새 신호 0 + 솔로에 무거운 인프라 2벌 = 오버엔지니어링 신호**(mp의 "필요없는 Kafka 거부" 성숙 신호와 모순). 오히려 기준 1순위에 어긋남을 발견하고 방향 전환.
  - **PaaS 경량 채택: 백엔드 Railway + DB Supabase(PostGIS) + 프론트 Vercel.** 대안 검토: (A)기존 k3s에 thin-add=인프라 일관성이나 단일노드(4OCPU/24GB) 용량 리스크+PostGIS 클러스터 구축 필요, (C)배포 보류. → PaaS가 (1)mp와 다른 배포 패러다임=**breadth**, (2)관리형 PostGIS로 용량 리스크 제거, (3)에너지를 간판(지리공간 백엔드)에 집중, (4)push→자동배포 즉시.
  - **호스트 선택 근거(2026 웹검증):** Railway = GitHub push 자동감지·자동배포, 실질 무료 크레딧 유지. Fly.io = 2024 무료티어 폐지(CC 필수·트라이얼만) → 배제. Supabase = PostGIS 등 확장 기본 내장(Neon은 serverless라 일부 확장 제약) + pgrouting(향후 루트 기능) 보유 → Neon보다 우위.
  - **진짜 새 DevOps 신호는 P4로 이연** — mp가 안 한 것(오토스케일링/HPA를 k6 부하테스트로 증명)이 additive. 지금 억지 배포 심화 대신, 부하 스토리와 함께 만들 때 가치.
- 트렌드 근거(웹검색): Railway Spring Boot 배포 가이드/무료티어, Fly.io 무료티어 폐지, Supabase vs Neon PostGIS 확장 비교(2026).
- 관련: `CLAUDE.md`(§7 인프라·§10 P5 개정), 레포 `github.com/hoeongj/geuneul`, CI run 28539003609(success)
- 다음 할 일: 배포 아티팩트(Dockerfile, DEPLOY.md, 프로파일) 준비 → 사용자가 Supabase/Railway 계정 연결 → skeleton 라이브 확인.

### 2026-07-02 — CD 재재검토: PaaS → AWS(ECS Fargate + RDS + Terraform)로 최종 선회
- 한 일: PaaS(Railway/Supabase) PR #1 닫고, **AWS 인프라를 Terraform으로 구축**(infra/terraform/*: VPC·RDS·ECS·ALB·ECR·IAM OIDC) + **GitHub Actions 배포 워크플로우(deploy.yml, OIDC)** + AWS용 DEPLOY.md. Redis 헬스체크 비활성(미프로비저닝 단계 ALB 헬스 방해 방지).
- 결정 & 이유(why):
  - **PaaS → AWS 선회 (사용자 지적 반영):** "회사가 실제 쓰는 걸 해야 취업에 유리". 국내 백엔드/DevOps JD가 요구하는 건 **AWS·컨테이너·IaC**(당근/배민=AWS, EKS/ECS/Terraform 우대). Railway/Supabase는 인디 툴이라 취업 신호 약함 → 배제. **교훈: 포트폴리오 기술선택 1순위 = 회사 실사용 스택.**
  - **ECS Fargate 채택**(EKS 아님): control plane 무료 vs EKS $73/월(프리 아님) + mp가 이미 k8s 증명 → 돈 대비 새 신호는 ECS로 충분. 대안 EC2+Docker(더 저렴하나 오케스트레이션 신호 약함) 검토 후 Fargate 선택.
  - **RDS PostgreSQL(PostGIS)**: 프리티어 db.t3.micro. PostGIS는 Flyway CREATE EXTENSION으로. RDS가 관리형이라 백업/가용성 확보.
  - **Terraform(IaC) + GitHub Actions OIDC**: mp에 없던 축(IaC·키없는 배포) = 새 포트폴리오 신호. OIDC로 장기 액세스키 제거(2026 베스트프랙티스).
  - **비용 절감 설계**: NAT 게이트웨이 없음(Fargate 퍼블릭 서브넷+SG 잠금), ECS containerInsights off, ECR 라이프사이클(10개), RDS 프리티어. 상시 비용은 ALB ~$16/월 → $200 크레딧으로 커버. mp의 "필요없는 것 안 쓰기" 성숙 신호와 정렬.
  - **재사용:** PaaS 브랜치의 Dockerfile·application.yml(PORT/redis)은 AWS에도 그대로 유효 → 이어감.
- 트렌드 근거(웹검색): AWS 프리티어 2026($200 크레딧, RDS 프리티어, ECS 무료/EKS $73), 국내 JD의 EKS/ECS/Terraform 우대.
- 관련: `infra/terraform/*`, `.github/workflows/deploy.yml`, `DEPLOY.md`, `CLAUDE.md`(§7), 닫힌 PR #1
- 다음 할 일: (검증) 사용자가 `terraform init/validate/plan`로 syntax 확인(로컬 terraform 미설치라 미검증) → AWS 계정 연결 → apply → skeleton 라이브. 이후 P1 백엔드 본론.

### 2026-07-02 — 🚀 AWS 라이브 배포 완료: push→자동배포 파이프라인 end-to-end 검증
- 한 일: AWS 계정 셋업(IAM 유저·CLI·예산알림 2중) → `terraform apply`(31 리소스, 중간에 TS-001 발생·해결) → GitHub 시크릿 `AWS_ROLE_ARN` 등록 → PR #2 머지 → **첫 자동배포 성공** → 라이브 검증.
- 검증된 것 (전부 실측):
  - **파이프라인**: `main` push → GitHub Actions(OIDC, 액세스키 없음) → Docker 빌드 → ECR push → ECS 태스크 리비전 → Fargate 롤링 배포 → 서비스 안정화. ✅
  - **앱**: ALB `/actuator/health` **HTTP 200 UP**, Swagger UI 200. ✅
  - **DB**: 앱 로그로 확인 — RDS 연결, **Flyway 2개 마이그레이션 적용(V1 PostGIS 확장 + V2 코어 테이블·GiST 인덱스)**, `Hibernate Spatial integration enabled: true`. ✅ → **RDS 위에 PostGIS 공간 스택이 실제로 살아있음.**
- 결정 & 이유(why):
  - **예산 알림을 콘솔 대신 CLI(`aws budgets create-budget`)로**: 실지출 $0.01 초과 + 월 $1 초과 예상 시 이메일. 콘솔 네비게이션 비용 절약 + 스크립트화.
  - **tfplan 파일 즉시 삭제 + gitignore 추가**: plan 바이너리에 변수값(DB 비번)이 포함됨을 인지 → 보안 처리. tfstate도 같은 이유로 gitignore(장기적으로 S3 백엔드 전환 예정).
  - **공개 문서에는 ALB URL만**(어차피 공개 엔드포인트), 계정 ID 포함 ARN·RDS 엔드포인트는 `.local/`에만 기록.
- 트러블슈팅: **TS-001**(SG description ASCII 제약 — `validate` 통과했지만 `apply` 런타임에서 발견, 부분 실패 후 멱등 재적용으로 복구) → `TROUBLESHOOTING.md` 참조.
- 관련: PR #2(머지), 커밋 `fbf86e9`, Deploy run 28548789100(success), **Live: http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com**
- 다음 할 일: **P1 지리 코어** — place 엔티티(JTS Point 매핑) → 공공데이터 idempotent 인제스천+지오코딩 → 반경/kNN/bounds API+Swagger → Testcontainers 공간쿼리 테스트. (P1 API DTO 확정 시 claude design 착수 신호.)

### 2026-07-02 — P1 지리 코어: 반경/kNN/bounds API + 무더위쉼터 idempotent 인제스천 (간판 착수)
- 한 일: Place 엔티티(JTS Point)·PlaceRepository(네이티브 공간쿼리 3종)·검색 서비스/컨트롤러/DTO·Swagger, **V3 geography 함수 인덱스**, 무더위쉼터 CSV 파서+JDBC 배치 upsert 인제스천, R__seed(숭실대 주변 10곳), **ADR 2건**(docs/adr/), 단위 3 + 통합 8 테스트.
- 결정 & 이유(why): — 상세는 ADR로 분리, 여기엔 요지만.
  - **[ADR-0001] geometry(4326) 저장 + `GIST(geography(geom))` 함수 인덱스** — "반경 N미터"를 도(degree) 근사 없이 정확하게 + 인덱스 완전 활용. 대안(geography 컬럼 저장/도 단위 근사/앱 레벨 필터/컬럼 이중화) 비교 기각. 근거: Paul Ramsey(PostGIS core) 함수 인덱스 패턴 (2026-07 웹검증).
    - 구현 디테일: `geom::geography` 표기는 Spring Data 네이티브 쿼리 파서가 `:geography`를 파라미터로 오인 → 동일 의미의 함수형 `geography(geom)` 사용. null enum 파라미터는 `CAST(:category AS text)`로 PG 타입추론 실패 방지.
  - **[ADR-0002] (source, source_external_id) 자연키 + ON CONFLICT 배치 upsert** — 재실행 멱등(CLAUDE.md 원칙 3) + 52k행 스케일 대비 JDBC 배치. 쉼터시설번호가 자연키, 없으면 sha256(name|address) 결정적 대체키. 파일 단위 단일 트랜잭션. 대안(JPA saveAll/전체 재삽입/Spring Batch/MERGE) 기각.
  - **파서 스키마 드리프트 내성** — 표준데이터 좌표 필드 존재가 문서상 미확정(2025-02 공중화장실 좌표 정책 변경 전례) → 헤더 별칭 매칭 + 좌표 결측 행 skipped 계수(지오코딩 백로그 근거 수치). 인코딩(UTF-8/CP949) 주입 가능.
  - **테스트 전략** — 파서는 순수 단위(오프라인 상시), 공간쿼리 정확성·멱등성은 실 PostGIS IT(반경 미터 단위 검증: 1.5km→2곳/500m→1곳, kNN 순서, bounds, 2회 적재 중복 0, DO UPDATE 갱신). 컨테이너는 base class static 공유로 CI 시간 절약.
  - **시드는 Repeatable 마이그레이션(R__)** — 체크섬 변경 시에만 재실행 + upsert 수렴. 데모용 지도 공백 방지, 실데이터가 대체.
- 관련: `backend/src/main/java/com/geuneul/domain/{place,ingest}/`, `V3__geography_functional_index.sql`, `R__seed_sample_places.sql`, `docs/adr/0001·0002`
- 다음 할 일: PR CI(실 PostGIS IT) → 머지·자동배포 → **라이브 반경검색 스모크 테스트** → 공중화장실(지오코딩 포함) 인제스천 + JaCoCo floor ratchet.

### 2026-07-02 — P1 완결: CI 트러블슈팅(TS-002) → 머지·자동배포 → 🔴 라이브 공간검색 실측 + 테스트 보강
- 한 일: PR #3 CI 실패 원인 규명·해결(**TS-002**: @Container(static)×Spring 컨텍스트 캐시 수명주기 충돌 → 싱글턴 컨테이너 패턴 전환) → CI green(실 PostGIS IT 11개) → 머지·자동배포 → **라이브 스모크 3종 실측** → 후속 품질 PR #4(컨트롤러 MockMvc 8케이스 + JaCoCo ratchet + 커버리지 아티팩트) 머지.
- 라이브 실측 결과 (ALB, 프로덕션 RDS PostGIS):
  - `GET /places?lat=37.4963&lng=126.9575&radius=1000` → **4곳, 거리순(20.9m→464.8m), 상도역(1.1km)은 정확히 제외** = geography 미터 반경이 프로덕션에서 실증.
  - `GET /places/nearest?category=TOILET&limit=1` → 사육신공원 공중화장실(2,765.9m) = kNN+카테고리 필터 동작.
  - `GET /places?bounds=126.91,37.48,126.97,37.52` → seed 10곳 전부 = 뷰포트 쿼리 동작.
- 결정 & 이유(why):
  - **[TS-002] 싱글턴 컨테이너 패턴** — @Container는 클래스 종료 시 컨테이너를 중지하지만 Spring TestContext는 컨텍스트를 JVM 캐싱 → 2번째 IT 클래스부터 죽은 포트 ConnectException. 수동 start() 싱글턴(공식 권장)으로 전환, 컨테이너 1세트 공유로 CI도 단축. 상세 TROUBLESHOOTING.md.
  - **컨트롤러 MockMvc 보강** — 공간쿼리 정확성은 IT가 커버하지만 컨트롤러 검증(400 계열·bounds 파싱·limit 클램프)은 사각지대였음 → @WebMvcTest 8케이스(DB 불필요, 로컬 상시 실행).
  - **JaCoCo floor 0.00→0.35 ratchet** — 로컬(단위만) 실측 38.1% 바로 아래. 로컬이 하한선이므로 IT까지 도는 CI는 항상 그 이상 = 어디서든 green 보장하면서 회귀만 차단(mp 철학). CI에 커버리지 아티팩트 업로드 추가(다음 ratchet 근거).
- 관련: PR #3(0526d00)·PR #4(3d11351), TROUBLESHOOTING TS-002, Live: `/places` 3종 엔드포인트
- 다음 할 일: **API 응답 DTO 확정됨 → claude design 착수 적기.** 백엔드는 공중화장실 표준데이터(52k, 지오코딩 보완 포함) 인제스천 → P2 UGC+인증(카카오/구글 OAuth, 제보/후기 2단).

### 2026-07-02 — 커버리지 프레임 교정: "숭실대 출시"가 아니라 "전국 적재 + 동작구 검증"
- 한 일: 사용자 지적("지도 데이터 전체에서 필터링해 보여주는 건데 왜 숭실대만?")을 반영해 CLAUDE.md·README의 커버리지 표현 전면 교정.
- 결정 & 이유(why): **공공데이터 레이어와 UGC 레이어를 구분.** ①공공데이터는 전국 표준데이터를 그대로 적재 — 범위 제한이 비용을 아끼지 않고(배치 upsert), 데이터가 많을수록 "대용량 지리검색" 간판이 강해짐. 지도는 어디서든 동작. ②"좁게 집중"은 콜드스타트가 있는 UGC·검증·마케팅에만 적용(동작구 필드테스트 = 당근의 판교식 하이퍼로컬 런칭). ③숭실대 브랜딩 제거 — mp가 이미 학교색이라 그늘은 범용 위치 서비스로 포지셔닝(코드는 원래 위치 무관이라 변경 없음).
- 관련: `CLAUDE.md`(헤더·비목표·P5), `README.md`
- 다음 할 일: 전국 무더위쉼터 + 공중화장실 표준데이터 전량 인제스천(수십만 포인트 → P4 부하테스트 스토리 강화).

### 2026-07-02 — 인제스천 소스 제네릭화(+공중화장실) · 운영 적재 경로(ECS one-off) · 디자인 브리프
- 한 일: 파서를 소스 제네릭 구조(`SourceSpec` enum + `StandardCsvParser`)로 리팩터링하고 **공중화장실 표준데이터**(WGS84위도/경도 별칭) 지원 추가. `--ingest.url`(원격 파일)·`--ingest.exit-after`(one-off 모드) 옵션, **프로덕션 적재 스크립트**(`infra/scripts/prod-ingest.sh` — ECS RunTask 커맨드 오버라이드), claude design 착수용 **디자인 브리프**(`docs/design-brief.md`).
- 결정 & 이유(why):
  - **소스별 파서 클래스 대신 SourceSpec enum + 범용 파서** — 소스 추가가 "enum 한 줄 + 별칭"으로 끝남. 무더위쉼터/화장실의 차이(컬럼명·카테고리·source키)는 전부 데이터였지 로직이 아니었음.
  - **운영 적재 = ECS one-off task(RunTask)** — RDS가 프라이빗 서브넷이라 로컬 접속 불가(의도된 설계). 대안 검토: (A)RDS 공개 전환=보안 후퇴 기각, (B)관리자 업로드 API=인증 없는 P1에서 공개 엔드포인트 리스크 기각, (C)배스천 호스트=상시 비용. → 서비스와 같은 태스크 정의(이미지·SSM 비밀·SG) 재사용하는 one-off가 무비용·무신규인프라로 정석. 데이터 스냅샷은 GitHub Release 자산으로 버저닝(레포 비대화 방지).
  - **data.go.kr 자동 다운로드 불가 확인** — 표준데이터셋 파일 다운로드는 로그인 세션 필수(엔드포인트 `selectFileDataDownload.do` 직접 호출 시 HTML 에러). 파일 확보만 수동 단계로 분리, 파이프라인은 URL 기반으로 완전 자동화.
  - **디자인 타이밍** — API 계약(PlaceResponse)이 라이브 검증까지 끝난 시점 = 재작업 없는 디자인 착수 적기. 브리프에 실측 JSON·카테고리 8종·survival_score 색상 예약 슬롯 명시.
- 관련: `SourceSpec/StandardCsvParser/PlaceRow`, `prod-ingest.sh`, `DEPLOY.md`(운영 인제스천), `docs/design-brief.md`, 테스트(파서 4·IT 3, 소스 충돌 방지 케이스 추가)
- 다음 할 일: 사용자로부터 표준데이터 CSV 2개 확보 → Release 업로드 → prod-ingest 실행 → 라이브 전국 검증.

### 2026-07-02 — 설계 Q&A: "공공데이터를 실시간으로 긁어야 하지 않나?" → 신선도 3층 정리
- 질문(사용자): 정보가 계속 바뀌는데 매번 실시간으로 가져와야 하는 것 아닌가?
- 결론: **아니오 — 데이터 층마다 신선도 전략이 다르다.** ①공공데이터(화장실/쉼터 위치)=물리 인프라, 원천 자체가 지자체의 주기 스냅샷이라 실시간 원천이 없음 → 자체 DB + 주기 동기화(업계 표준, 카카오/네이버도 자체 POI DB). ②날씨=분 단위 → 실시간 호출+Redis TTL 캐시(P3). ③"지금 시원한지" 같은 진짜 실시간 = 어떤 공공 API도 제공 안 함 → **유저 제보 freshness가 담당(그늘의 존재 이유)**.
- 매 요청 공공 API 프록시를 기각한 기술 근거: PostGIS 반경/kNN 인덱스 상실(간판 불가) · rate limit/지연 · 가용성 결합.
- 반영: 멱등 upsert(ADR-0002)가 이미 재실행 안전 → **주기 동기화 스케줄(EventBridge→ECS RunTask)을 P3 로드맵에 추가**(수동 재적재의 자동화, dom의 월간 크론 패턴과 동일).
- 관련: `CLAUDE.md` §10 P3

### 2026-07-02 — 설계 Q&A 후속: 주기 동기화의 삭제(폐쇄) 케이스 식별
- 사용자 멘탈모델("주기적으로 긁어서 변한 것만 반영") = 현 설계와 일치 확인 — 멱등 upsert가 diff 확인·반영을 한 문장으로 흡수(변경분만 갱신, IT 검증됨).
- 이 대화에서 발견한 갭: **스냅샷에서 사라진 행(화장실 폐쇄 등)은 upsert가 못 지움** → P3 동기화에 "미출현 (source, external_id) soft-delete 비활성화" 추가(하드 삭제는 제보/후기 FK 파괴라 기각). + 오픈API serviceKey 확보 시 다운로드까지 무인 루프.
- 관련: `CLAUDE.md` §10 P3

### 2026-07-02 — 지오코딩 파이프라인: 화장실 표준데이터의 좌표 소멸(2025-02)에 대응
- 한 일: 실측으로 **공중화장실정보.csv(59,768행)에 좌표 컬럼이 아예 없음**을 확인(CLAUDE.md가 경고했던 2025-02 정책 변경의 실물) → **카카오 지오코딩 파이프라인** 구현. 파서에 BOM+따옴표 정규화·행안부(safetydata) 영문코드 헤더(LA/LO)·이중헤더 라벨행 차단 추가. 데이터 품질 검사(관리번호 중복 0·주소 결측 0 = 전량 지오코딩 가능).
- 결정 & 이유(why): — 상세 ADR-0003.
  - **geocode-before-insert** (geom NOT NULL 유지 — "places에 있으면 반드시 지도에 찍힌다" 불변식) vs 대안(nullable geom=유령 행, 별도 캐시 테이블=중복) 기각.
  - **저장 좌표 재사용** — geocoded=true + 주소 불변이면 재호출 스킵 → 주기 동기화가 쿼터를 재소모 안 함. IT로 "2차 실행 시 성공분 호출 0회" 검증.
  - **트랜잭션 경계 재설계**(ADR-0002 개정) — 60k 외부 HTTP를 단일 tx에 넣지 않고 upsert 단위 tx로. 멱등이라 부분 실패도 재실행 수렴.
  - **가상 스레드(Java 21) + Semaphore(8)** — I/O 병렬은 넓게, 카카오 QPS는 상한 보호. 429 백오프 3회.
  - 파서 견고성: BOM이 여는 따옴표 앞에 오면 CSV 파서가 비인용 필드로 읽어 헤더 키에 따옴표가 남는 실전 케이스 발견 → 정규화로 흡수(단위테스트 고정).
- 실측 데이터 품질(적재 전 검사): 화장실 59,768행 — 관리번호 유니크·주소 100%·좌표 0%. 무더위쉼터 파일은 **전국 샘플 100행**(전체분은 safetydata 별도 확보 필요, 지역코드 10개 분포 확인).
- 관련: `geocode/`, `StandardCsvParser`(BOM/이중헤더), `SourceSpec`(영문 별칭), ADR-0003, 테스트 22→28개
- 다음 할 일: PR→배포 → 데이터 Release 업로드 → 쉼터(키 불필요) 적재 → **카카오 REST 키 수령 후** 화장실 60k 지오코딩 적재 → 라이브 전국 검증.

### 2026-07-02 — 핫픽스: Boot 4에서 RestClient.Builder 빈 부재 → 전 IT 컨텍스트 실패 (+프로세스 반성)
- 증상: PR #6 CI에서 모든 IT가 `NoSuchBeanDefinitionException`(컨텍스트 생성 실패). 로컬은 Docker 없어 IT skip이라 미검출.
- 원인: Boot 4 모듈화로 RestClient 자동구성이 별도 모듈로 분리 → `RestClient.Builder` 빈이 기본 클래스패스에 없음. KakaoGeocodingClient가 이를 생성자 주입 → 컴포넌트 스캔 시점에 전 컨텍스트 폭발(테스트는 @Primary 페이크가 있어도 실제 컴포넌트 생성은 시도됨).
- 해결: 정적 `RestClient.builder()`로 전환(의존성 추측 없이 결정적). 배치 전용 클라이언트라 자동구성 이점(계측) 대비 단순화 우위.
- **프로세스 반성:** CI 종료코드를 파이프(tail) 뒤에서 읽어 **빨간 CI를 green으로 오판, 머지까지 진행**함. 롤링 배포가 기존 태스크를 유지해 서비스 영향은 없었으나, 이후 자동화 스크립트는 `PIPESTATUS[0]`로 판정하도록 교정. main이 붉은 시간 최소화를 위해 즉시 fix-forward.
- 관련: `KakaoGeocodingClient.java`

### 2026-07-02 — 🇰🇷 전국 데이터 라이브 개통: 첫 프로덕션 인제스천 + 배포 서킷브레이커
- 한 일: ①배포 사고 분석(TS-003: 빨간 CI 머지→크래시루프→ECS 런치 백오프로 5시간 수렴 지연 + 워크플로우 false-negative) → **ECS deployment circuit breaker + 자동 롤백** 적용(terraform, 무중단). ②공공데이터 스냅샷을 GitHub Release(data-v1)로 버저닝. ③**첫 프로덕션 인제스천 성공** — ECS one-off task가 Release에서 다운로드→이중헤더 라벨행 1건 스킵→**전국 무더위쉼터 100건 upsert(902ms)**→클린 종료. ④라이브 전국 검증: 부산 덕천 반경검색(4.3m 정확도)·대전역 kNN 3곳 — 서울 밖 어디서나 동작.
- 결정 & 이유(why):
  - **서킷브레이커+롤백** — 재발 시(부팅 불가 이미지) 수 시간 백오프 대신 즉시 롤백. lifecycle ignore_changes 밖의 속성이라 terraform in-place로 안전 적용.
  - **데이터 스냅샷 = GitHub Release 자산** — 레포 비대화 없이 버저닝(data-v1), 인제스천은 URL만 바꾸면 재실행. 출처·행수·인코딩을 릴리즈 노트에 명시.
  - 무더위쉼터 파일이 **전국 샘플 100행**임을 확인(지역코드 10개 분포) — 전체분은 행안부 safetydata 확보 필요(P3 주기 동기화에서 오픈API로 무인화 예정).
- 실측: RunTask 전체 3분(프로비저닝 포함), 인제스천 자체 902ms. 화장실 59,768행은 카카오 키 대기(지오코딩 필수 — ADR-0003).
- 관련: `infra/terraform/ecs.tf`, Release data-v1, TROUBLESHOOTING TS-003, 태스크정의 :7
- 다음 할 일: 카카오 REST 키 수령 → 화장실 60k 지오코딩 적재(~수십 분) → P2(UGC+인증) 착수. 디자인은 브리프(docs/design-brief.md)로 병행 가능.

### 2026-07-02 — 문서 전수 감사(멀티에이전트) → 15개 결함 수정
- 한 일: 공개 문서 전체를 다각도 감사(에이전트 워크플로우: 문서별 8 + 횡단/비밀 2, 발견 후 적대적 검증). 세션 한도로 검증 단계 일부가 죽어 원발견 15건을 journal에서 회수해 **직접 사실 대조 후 전량 수정**.
- 수정한 결함(코드와 대조하면 드러날 것들):
  - **k3s 잔재**: docker-compose.yml 헤더가 "프로덕션=k3s+ArgoCD"로 남아있던 것(실제 AWS) → 교정. (high)
  - **수치 불일치**: 화장실 건수 CLAUDE.md "약 52,255건" → 실측 59,768행. ADR-0002 "52k"→60k. "일부 좌표 미제공"→전량 미제공. (medium)
  - **ADR-0002 노후화**: 관련 클래스명 `CoolingShelterCsvParser`(삭제됨)→`StandardCsvParser`; 결정3 "파일 단위 트랜잭션"→ADR-0003 개정 반영; 결정4 skipped 의미가 지오코딩 후보 도입으로 바뀐 것 반영. (high/medium)
  - **코드 인용 불일치**: TS-002의 `DockerClientFactory.isDockerAvailable()`→`.instance().isDockerAvailable()`; 재시도 횟수 "3회"→"총 3회 시도(최대 재시도 2회)"(ADR-0003 + javadoc 동기화). (low)
  - **라이브 현실 반영**: README API 예시 category=TOILET(미적재)→COOLING_SHELTER(전국 적재분)+부산 예시; design-brief 에러포맷 "RFC7807 ProblemDetail"(미설정)→Spring 기본 오류 JSON(라이브 실측); DEPLOY.md에 KAKAO 키 전달법·paths 필터·Fargate 비용($12/월) 추가. (medium)
  - TS-001 커밋 참조 placeholder→8488eb6.
- 비밀 스캔: git 추적 68파일 전수 — AKIA키·비번·학번·서버IP·Gemini키 0건, tfstate/tfvars/jar 실수 커밋 0. ✅
- 결정 & 이유(why): 검증 에이전트가 죽었어도 원발견을 버리지 않고 **직접 코드 대조로 재검증**(빠뜨리면 오히려 문서 신뢰도 훼손). 과거 WORKLOG 항목의 당시 수치(52k 추정)는 역사 기록이라 보존하되, 살아있는 스펙/ADR/README만 실측치로 교정.
- 관련: CLAUDE.md·README·DEPLOY·TROUBLESHOOTING·docker-compose·docs/adr/0002·0003·design-brief, KakaoGeocodingClient(주석)
- 다음 할 일: 카카오 REST 키 수령 후 화장실 60k 지오코딩 적재 → P2(UGC+인증).

### 2026-07-02 — 화장실 60k 지오코딩 버그 수정(TS-004): Jackson 3 record + 실 파싱 테스트
- 한 일: 카카오 REST 키 수령·검증(단건 200) → 화장실 59,768행 프로덕션 지오코딩 실행 → **전량 실패(geocoded=0)** 발견 → 원인규명(TS-004: Boot 4 Jackson 3가 Jackson 2 JsonNode 역직렬화 실패, 게다가 IT가 페이크 지오코더라 실 파싱 미검증) → JsonNode를 타입 record로 교체 + MockRestServiceServer 단위테스트 5건 추가.
- 결정 & 이유(why):
  - **JsonNode → record(`KakaoAddressResponse`)**: Jackson 3(`tools.jackson`)는 Jackson 2 `com.fasterxml...JsonNode`를 모름. record는 이름 매핑이라 버전 무관·타입 안전. (도로명 우선/지번 폴백 로직 유지.)
  - **MockRestServiceServer 테스트**: 외부 API를 페이크로 대체한 IT는 "우리 로직"은 검증하되 "그 API와의 실제 계약(역직렬화)"은 미검증 사각지대를 남긴다 — 이번 사고의 근본. 실 RestClient 변환기를 경유하는 단위테스트로 그 계약을 고정(로컬 실행, Docker 불필요).
  - 파이프라인이 멱등이고 실패분은 캐시 안 되므로(성공만 geocoded=true 저장) 재배포 후 재실행 시 54,090건 전량 재시도 → 수렴.
- 관련: `KakaoGeocodingClient`(record), `KakaoGeocodingClientTest`(5건), TROUBLESHOOTING TS-004
- 다음 할 일: PR→CI green→머지→배포 → 화장실 재적재 → 라이브 전국 검증 → P2 or 프론트.

### 2026-07-02 — 화장실 전국 라이브 + 배포 서킷브레이커 오롤백 튜닝(TS-005)
- 한 일: Jackson 수정 배포 → 화장실 **재적재 성공(geocoded=46,897 / skipped=5,678 / geocodeFailed=7,193)** → 광화문·부산 라이브 화장실 검증 통과 = **전국 화장실 데이터 라이브.** 단, PR#10 배포가 서킷브레이커에 오롤백(서비스가 옛 이미지 유지)된 것 발견 → 유예 240초로 튜닝 후 재배포.
- 결정 & 이유(why):
  - **헬스체크 유예 120→240초**: 0.25 vCPU 부팅 93초 + ALB 헬스체크(30s×2)까지 ~150초 필요한데 유예 120초라 서킷브레이커 오롤백(TS-005). 근본은 CPU 부족이나 저트래픽·비용 우선이라 유예 확대로 해결(트래픽 시 CPU 512).
  - **geocodeFailed 7,193(≈12%)**: 카카오가 못 찾는 주소(구주소·오타·신주소 미반영 등). 실패분은 캐시 안 되니 다음 동기화에서 재시도. 46,897건(≈88%) 적재로 실용 커버리지 충분.
  - 데이터 스냅샷은 GitHub Release(data-v1) 재사용 — 재적재는 URL만 동일하게.
- 관련: `infra/terraform/ecs.tf`(grace 240), TROUBLESHOOTING TS-005, ECS task 2ea175e5(재적재), Release data-v1
- 다음 할 일: 재배포 안정화 확인 → P2(UGC+인증) 또는 프론트(새 세션). 프론트는 이제 TOILET 카테고리도 전국 데이터로 동작.

### 2026-07-03 — 프론트엔드 MVP 4화면(Next 16 App Router + PWA) 라이브 API 연동
- 한 일: 하이파이 디자인 핸드오프를 대상 스택으로 재현 — **홈 지도 / 장소 상세 / 지금 급해요 / 제보(프리뷰)** 4화면 + 하단 3탭 + 상세 오버레이. 라이브 백엔드(bounds/radius/nearest/단건)에 **서버 프록시**로 연결. `pnpm typecheck`·`lint`·`build` 통과, `next start` 로 프록시 4종 + 페이지 런타임 스모크 검증(전국 화장실 데이터까지 조회 확인).
- 결정 & 이유(why) — 라이브러리(2026 트렌드 확인):
  - **Serwist(@serwist/next)**: next-pwa 는 유지보수 정체 → 후계인 Serwist 가 Next 공식 문서 권장. 매니페스트·SW·오프라인 셸·설치가능.
  - **TanStack Query v5**: 시장 표준(주간 12.3M vs SWR 7.7M). `queryKey=bounds` + `keepPreviousData` 로 뷰포트 패닝 중 과호출·깜빡임 억제(백엔드 Redis 캐시와 이중 방어), P2 mutation(제보/후기 optimistic) 대비.
  - **react-kakao-maps-sdk**: `useKakaoLoader`+`MarkerClusterer`. 커스텀 마커는 **SVG data-URI**(카테고리 아이콘+회색 링+꼬리)로 생성해 클러스터링과 디자인 픽셀을 동시에.
  - **Tailwind v4 `@theme`**: 핸드오프 디자인 토큰(색/반경/그림자)을 테마 변수로 1:1 매핑 → 픽셀 재현+유지보수.
  - **자체 `<Icon>`**(프로토타입 `svgIcon` 재현): UI 아이콘과 마커 data-URI 가 **같은 path 소스**를 공유(일관성). `WATER` 는 원본 svgIcon 의 water 키가 비어 있던 갭이라 표준 droplet path 로 보강.
- 핵심 설계:
  - **서버 프록시(app/api/**)**: ALB 가 http(TLS 없음)+CORS 미설정이라 브라우저 직접 호출은 mixed-content/CORS 로 차단됨. 브라우저는 동일 오리진 `/api/*` 만, Next Route Handler 가 서버 전용 env `GEUNEUL_API_BASE`(NEXT_PUBLIC 아님)로 ALB 프록시 → 두 제약 동시 회피 + ALB 호스트 미노출.
  - **급해요 = 서버 팬아웃**: nearest 는 category 단일 파라미터라, 다중 카테고리 시나리오(잠깐 쉬어갈 곳=COOLING_SHELTER/LIBRARY/UNDERGROUND/CIVIC 등)를 `/api/urgent` 에서 카테고리별 병렬 호출→id 중복 제거→거리순→top5 로 병합.
  - **장소 상세 = 라우트가 아닌 오버레이**: 스펙이 "콘텐츠만 덮고 탭바 유지+슬라이드업"이라 클라이언트 상태 오버레이가 정확히 부합(공유 딥링크 URL 은 Reserved).
  - **키 없이도 동작**: Kakao JS 키가 없으면 지도만 placeholder, 리스트/급해요/상세 데이터는 프록시로 정상 동작 → 키 주입 즉시 실지도 점등.
  - **범위 준수**: P2/P3(후기·제보 POST·로그인·freshness·AI 요약·survival_score 3색)은 레이아웃 자리만.
- 관련: `frontend/`(전체), `frontend/README.md`, `.github/workflows/frontend-ci.yml`(paths:frontend/**, typecheck·lint·build), TROUBLESHOOTING TS-006
- 다음 할 일: PR→프론트 CI green→머지. Kakao JS 키 수령 시 `.env.local` 주입해 실지도 확인. 이후 P2(UGC+인증) 또는 상세 미니맵 실지도화.

### 2026-07-03 — 프론트엔드 Vercel 프로덕션 배포 (App Live)
- 한 일: 프론트 MVP 를 **Vercel 프로덕션 배포** → **https://geuneul.vercel.app** 라이브. Kakao 실지도 + 라이브 데이터(전국 화장실 등) 동작 확인.
- 결정 & 이유(why):
  - **로컬 대신 배포 우선**: Kakao JS SDK 는 등록된 도메인에서만 로드되는데, 콘솔이 **localhost 등록을 계속 거부**(알려진 이슈). 실도메인은 정상 등록되므로 Vercel 배포로 우회 — 어차피 브리프상 배포 타깃이 Vercel 이라 정공법.
  - **`vercel.json` 로 빌드 고정**: `buildCommand=pnpm build`(=`next build --webpack`) — Vercel 기본 `next build`(Turbopack)는 Serwist(webpack)와 충돌하므로 webpack 강제. `installCommand=... --ignore-scripts` — pnpm 11 이 CI 에서 미빌드 네이티브 스크립트를 하드에러 처리하므로 무시(sharp 미사용·oxide prebuilt·unrs JS 폴백).
  - **env 분리**: `GEUNEUL_API_BASE`(서버 전용) + `NEXT_PUBLIC_KAKAO_MAP_JS_KEY` 를 Vercel Production 에 암호화 저장. 키는 레포 미포함(.env.local·Vercel env 만).
- 트러블: Kakao 도메인이 계속 거부 → SDK 응답(`AccessDeniedError: domain mismatched`)으로 **브라우저 없이 등록 여부를 직접 검증**. 원인은 도메인을 **"제품 링크 관리 > 웹 도메인"(카카오톡 공유용)** 에 넣은 것 — 지도는 **"JavaScript 키 > JavaScript SDK 도메인"** 에 등록해야 함. 올바른 칸 등록 후 SDK 정상 로드 확인.
- 관련: `frontend/vercel.json`, Vercel project `geuneul`(prod alias geuneul.vercel.app), PR #12
- 다음 할 일: PR #12 머지 → main. P2(UGC+인증)는 **백엔드 API(POST /reports·/reviews·/auth) 먼저** 필요 — 별도 착수.

### 2026-07-03 — 상세 미니맵 실지도화 + Vercel git 자동배포 파이프라인 완성
- 한 일: ① 장소 상세 미니맵을 placeholder → **선택 장소 중심 Kakao 실지도**(비대화형, 키 없으면 폴백)로 교체(PR #14). ② Vercel 프로젝트에 **rootDirectory=frontend 설정 + GitHub 레포 연결** → `main` push 시 프론트 자동배포. ③ 그 전환이 유발한 git 배포 ENOENT(TS-007)를 `process.env.VERCEL` 가드로 수정하고 자동배포 Ready 검증.
- 결정 & 이유(why):
  - **미니맵 비대화형(드래그·줌 off)**: 상세 화면 미니맵의 역할은 "위치 한눈에"지 탐색이 아님 — 스크롤 제스처와의 충돌(지도 팬이 페이지 스크롤을 삼키는 모바일 고질병)을 원천 차단.
  - **자동배포 = Vercel git 연결**(GitHub Actions로 프론트 배포 워크플로 추가하지 않음): Vercel 네이티브 경로가 preview 배포·롤백을 공짜로 제공, 워크플로 이중화 불필요. 백엔드(deploy.yml)와 트리거 경로도 자연 분리(frontend/** vs backend/**).
  - **Kakao 도메인 검증을 curl Referer로**: 브라우저 없이 `dapi.kakao.com/v2/maps/sdk.js`에 Referer만 바꿔 질의하면 등록 여부가 즉시 판별됨(성공=SDK JS, 실패=AccessDeniedError). 콘솔 등록 삽질(제품링크관리 vs JavaScript SDK 도메인 칸 혼동)을 이 방법으로 역추적해 해결.
- 관련: `frontend/components/place/DetailMiniMap*.tsx`, `frontend/next.config.ts`, `frontend/vercel.json`, TROUBLESHOOTING TS-007, PR #14, 커밋 a0bc0f1
- 다음 할 일: P2 착수 — 제보(reports) API 백엔드부터(익명 허용이라 외부 자격증명 불필요). OAuth/후기는 카카오·구글 콘솔 설정(사용자 액션) 선행.

### 2026-07-03 — P2 제보 기능 적대적 리뷰 + 보안 하드닝(XFF 신뢰경계·OOM)
- 한 일: 제보 API(백엔드 PR #15 배포됨)와 프론트 연동(PR #16)을 **다중 에이전트 적대적 코드리뷰**(19 에이전트·5차원, 각 발견 반증 검증)로 감사. 확정 결함 2대(레이트리밋 XFF 위조 우회 + eviction no-op OOM)를 백엔드에서 수정. 상세는 TROUBLESHOOTING TS-008.
- 결정 & 이유(why):
  - **BFF-공유시크릿 신뢰경계(ProxyClientResolver)**: ALB가 실 IP를 XFF 최우측에 append하는 구조에서 "최좌측=클라이언트"는 위조 가능 → 리밋 우회. 프록시(BFF)를 신뢰경계로 승격해 시크릿 증명 시에만 BFF가 준 클라 IP를 신뢰. **회귀 없는 점진 활성화**(시크릿 미설정=기존 최좌측 동작)로 프로덕션 무중단 — 활성화는 config 한 번.
  - **인메모리 리밋 유지(Redis/Bucket4j 도입 안 함)**: 단일 Fargate 태스크라 인메모리로 충분(2026 관례: 단일 인스턴스 in-memory→수평확장 시 Redis). eviction만 하드 상한으로 고쳐 OOM 차단.
  - **적대적 검증으로 거짓양성 배제**: 14 발견 중 9건(TOCTOU soft-limit, place 삭제 레이스 등)은 반증에서 "무해/acceptable tradeoff"로 판정해 수용 안 함 — 신호 대 잡음 관리.
- 관련: `ProxyClientResolver`(+Test 7), `ReportRateLimiter`(evict 하드상한·OOM 회귀 테스트), `ReportController`, `application.yml`(geuneul.proxy-secret), 리뷰 워크플로 wf_bac51fa8-52d
- 다음 할 일: 백엔드 PR→CI→배포. 프론트 PR #16에 BFF 시크릿 헤더 + nearest 에러상태 추가 후 머지. proxy-secret 활성화는 아침 체크리스트(Vercel env + 백엔드 SSM/env).

### 2026-07-04(새벽) — P2 제보 기능 완결·라이브 (백엔드+프론트+하드닝)
- 한 일: P2 제보 3-PR을 완결 라이브. **PR #15**(백엔드 API), **PR #16**(프론트 실전송+상세 최근제보+BFF 시크릿 헤더+nearest 에러상태), **PR #17**(적대적 리뷰 하드닝 TS-008). 라이브 E2E 통과: geuneul.vercel.app 프록시 경유 `POST /reports` 201(TTL 정확)→`GET /places/{id}/reports` 반영, 동일 XFF 4연속 4번째 429.
- 결정 & 이유(why):
  - **익명 제보 우선(로그인 후행)**: 제보는 "한 탭 낮은 부담"이 핵심 가치라 익명 허용이 제품상 옳고, OAuth 콘솔 설정 없이도 풀스택으로 완결·검증할 수 있다. 로그인 신뢰도 가중은 엔티티에 경로만 열어둠(`user_id` nullable).
  - **적대적 리뷰를 배포 후 자기검증에 사용**: 다중 에이전트 리뷰로 XFF 신뢰경계·OOM을 스스로 발견, 반증 검증으로 거짓양성 9건 배제 후 실결함 2건만 하드닝. "리뷰 = 발견+반증"으로 신호 관리.
- 산출물: `domain/report/*`(엔티티·TTL·리포·서비스·컨트롤러·리미터·리졸버 + dto), 프론트 `api/reports`·`api/places/[id]/reports`·`lib/reports`·제보 화면 실전송·상세 최근제보. 테스트 백엔드 24건(리졸버7·리미터6·컨트롤러7·IT4→CI) + 프론트 E2E.
- 관련 문서: TS-008, ADR-0004, HANDOFF 아침 체크리스트(proxy-secret 활성화·OAuth 준비).
- 다음: 아침 체크리스트대로 ① proxy-secret 활성화(선택) ② OAuth 콘솔 준비 → 로그인/후기 → survival_score(P3, 제보 데이터 축적됨).

### 2026-07-04(새벽) — proxy-secret 활성화(라이브): XFF 위조 우회 완전차단
- 한 일: TS-008 하드닝의 신뢰경계를 **라이브 활성화**. SSM `/geuneul/proxy_secret`(Terraform, IAM은 `/geuneul/*` 와일드카드라 무변경) + 태스크데프 rev13(실행 리비전 기반, 이미지 보존) 재배포 + Vercel env·재배포. 헤더 고정 테스트로 검증(유효 시크릿→X-Client-Ip 신뢰 유저별 리밋 / 위조→최우측 키잉 우회차단).
- 결정 & 이유(why):
  - **태스크데프 ignore_changes 우회 = CLI 리비전 등록**: ecs.tf가 `ignore_changes[container_definitions]`(CI가 이미지 관리)라 secrets 편집이 TF로 안 먹음 → 실행 중 리비전을 describe→시크릿 주입→register→update-service로 라이브 반영(deploy.yml 관례와 동일 경로). ecs.tf에도 문서화용 추가(fresh apply 대비).
  - **egress 가변 환경의 검증법**: "XFF 회전 시 429" 직접 테스트는 샌드박스 egress IP가 요청마다 바뀌면 최우측도 바뀌어 무결론 → X-Proxy-Auth/X-Client-Ip를 고정한 헤더 시뮬레이션으로 결정적 검증(관측 IP에 의존 안 함).
- 관련: `infra/terraform/{ssm,variables,ecs}.tf`, SSM `/geuneul/proxy_secret`, 태스크데프 geuneul:13, Vercel env, 시크릿은 `.local/proxy-secret.env`(gitignore)
- 다음: 레포 전수 품질 감사(포트폴리오 완성도).

### 2026-07-04 — 포트폴리오 품질 감사(다중 에이전트) + 32건 개선
- 한 일: 레포 전수를 7영역(백엔드 도메인·테스트/설정·프론트 컴포넌트·프론트 lib·인프라·문서·레포위생)으로 나눠 다중 에이전트 병렬 감사 → 각 지적을 "실제 개선인가" 적대적 검증(38 제기 → 32 확정) → 우선순위대로 적용(PR #18–21). 코드/문서를 한 줄씩 정독한다는 기준으로.
- 주요 개선 & 이유(why):
  - **간판 기능(M1)**: 반경/최근접의 표시 거리를 애플리케이션 하버사인(구체)으로 재계산하던 것을 제거하고 쿼리가 정렬식과 동일한 타원체 `ST_Distance`로 반환(PlaceDistanceView) → 표시=정렬 일치·이중계산 제거. "이중계산 방지" 주석과 코드의 모순도 해소.
  - **동시성(M3)**: 레이트리미터의 두 맵+AtomicInteger check-then-act TOCTOU를 단일 맵 `compute()` 원자 판정으로 교체. 시간소스는 Clock 빈으로 통일(L2).
  - **CI/CD(H2)**: `deploy.yml`에 테스트 게이트(`needs: test`) — 이전엔 테스트 실패 코드도 프로덕션 배포 가능했음. 액션 SHA-pin(M5)·gitleaks CI(M12)로 공급망·비밀 위생 강제.
  - **인프라(M6)**: RDS `storage_encrypted`(신규 대비, 라이브는 immutable이라 ignore_changes로 replace 방지+마이그레이션 백로그).
  - 데드코드·중복·매직넘버·토큰 불일치·문서 부정확(무더위쉼터 "전국 적재"→샘플 100건 등) 정리.
- 검증 방법론: 적대적 검증으로 거짓양성 배제(예: place 삭제 레이스=FK 안전, TOCTOU 일부=soft-limit 허용). 위험 지적은 직접 검증해 오판 차단 — 예: V2 마이그레이션 주석 변경은 Flyway 체크섬 검증이 깨져 부팅 실패하므로 제외, RDS backup_retention=7은 프리티어 제약이라 제외.
- 관련: PR #18(거리)·#19(백엔드 정리)·#20(프론트 정리)·#21(CI/인프라), 감사 워크플로 wf_7d7d81df.

### 2026-07-04 — 카공맵 기능 조사(다중 에이전트) + 흡수 1탄(자리 여유 제보) 라이브
- 한 일: 카공맵류(카공맵·유사앱 리뷰·GitHub `awesome-cafe`/`Buzzzzing`·Workfrom·누클) 다중 에이전트 조사 → ADR-0005(흡수 전략) 수립 → **최우선 항목(실시간 자리 여유/혼잡 제보) 백엔드+프론트 라이브**(PR #22). SEAT_OK/CROWDED enum 추가(스키마 무변경, TTL 2h), E2E 검증(직접+Vercel, 최근제보 반영, 잘못된 타입 400).
- 결정 & 이유(why):
  - **카공 = 별개 도메인이 아니라 그늘 '여름 실내 오래 버티기'의 부분집합**: 카공 조건(냉방+앉을곳+콘센트+눈치안봄+자리여유)이 그늘 여름 실내 피난과 정확히 포개짐. → 별도 앱/브랜드/CAFE 카테고리 신설(정체성 희석) 대신, **간판(지리공간+실시간 UGC 스코어링)을 키우는 데이터축·실시간 상태만 흡수**.
  - **리뷰앱화 거부(CLAUDE.md §9)**: aspect 별점 UI 주인공·예약/결제·리워드·소셜 팔로우·가격필터는 dilution으로 게이트. 커뮤니티/후기는 '살'로만 유지.
  - **자리 여유를 1탄으로**: 조사에서 '자리없음·헛걸음'이 카공/카페 최대 불만 + '도착 전 좌석확인' 재사용의향 80%. survival_score freshness로 굴러갈 킬러 신호인데 enum만 추가라 비용 최소·정체성 유지(여름 라벨).
- 관련: `docs/adr/0005`, `ReportType`(SEAT_OK/CROWDED), 프론트 REPORT_META/GRID, 조사 워크플로 wf_d23510d2, PR #22
- 다음(방향 확인 후): survival_score(P3)·GPS 방문인증·place_features 등급화 등 ADR-0005 나머지. OAuth는 사용자 콘솔 선행.

### 2026-07-04 — 공부 가능 공간 데이터 확장 계획(다중 에이전트 조사) — ADR-0006
- 한 일: "공부 가능한 카페 + 공공 공부공간(노들서가류) 전부 넣고 싶다" 요청을 다중 에이전트로 조사(공공 공부공간 데이터셋·노들서가류·카페 데이터·스키마 모델링, wf_e524daf8) → ADR-0006 수립. 데이터 다운로드 가능성 타진(odcloud/상권정보 API는 serviceKey 없이 401, 서울열린데이터 sample만 5행).
- 결정 & 이유(why):
  - **이건 UGC 기능이 아니라 데이터 커버리지 확장** → CLAUDE.md §3 커버리지 원칙("전국 표준데이터 그대로 적재") 정합, 간판(대용량 지리검색+idempotent ETL) 직접 강화. places 15만+(화장실6만+카페9만+도서관1.5만)로 커져 k6 부하테스트(P4) 소재.
  - **카테고리 최소 응집**: category(장소 kind)에 CAFE/STUDY_CAFE 2개만. '공부 가능'은 cross-cutting → place_features(study_ok/quiet). enum 남발은 지도 필터/색상만 복잡. 상업/커먼스 분리는 is_commercial 플래그(공개 커먼스 정체성 방어).
  - **카페 좌표는 소상공인 상권정보(WGS84 내장)** — LOCALDATA(EPSG:5174 재투영·지오코딩 폭증) 대신. 지오코딩 0건이라 화장실 6만 적재 파이프라인 규모 내.
  - **'공부 가능'은 공공데이터에 없음** → 전량 적재 + UGC 태깅(카공맵도 100% 크라우드소싱). STUDY_CAFE/도서관만 defaultFeatures로 study_ok 자동부여(낮은 confidence).
  - **정체성**: 카페는 '카페 추천앱'이 아니라 '시원한 실내 오래 버티기' survival 레이어. survival feature로만 태깅, 리뷰 UI 주인공化 금지(§9, ADR-0005 경계 유지).
- **의도적으로 지금 코드 안 넣은 이유**: 정확한 CSV 컬럼 헤더·상권 업종코드는 실제 파일로 확정해야(하드코딩 금지). 빈 enum·미검증 파서를 선심으면 재작업 + 빈 필터 UX. serviceKey/CSV 확보 시 enum+스키마+파서+적재를 실데이터로 한 번에(rule 1·2).
- 블로커: 공공데이터포털 오픈API serviceKey(무료 5분 가입) 또는 CSV 파일. serviceKey면 P3 무인화까지 연결.
- 관련: `docs/adr/0006`, HANDOFF §⑤, 조사 wf_e524daf8

### 2026-07-04 — 전 문서 정합 감사(다중 에이전트) + 세션 인계 정비
- 한 일: 세션 마무리 겸 전 문서(README·HANDOFF·WORKLOG·TROUBLESHOOTING·CLAUDE·design-brief·ADR 0001~0006)를 4축 병렬 에이전트로 코드·라이브와 대조 감사(wf_dc846959) → 확증된 정합 결함 **10건 수정**. HANDOFF 최상단에 "▶ 세션 인계" 앵커 추가(다음 세션 즉시 재개용).
- 수정 요지(전부 코드/라이브 실측 대조): SEAT_OK/CROWDED(PR #22) 반영 누락 — README 제보 서술·CLAUDE ERD report_type(9→11종)·design-brief 이모지 그리드 / HANDOFF 아키맵 ADR 범위(0001~0004→0006)·CAFE '채택'→'제안' 과표현 교정·현재 rev17 명시 / ADR-0005↔0006 상호참조 링크·ADR-0006 상태 문구(카테고리 미반영 명시) / ADR-0003 지오코딩 결과 실적재량(60k→실측 46,897) / WORKLOG 테스트 카운트 산술오류(23→24, 리미터7→6, PR #19 원자화 반영).
- 결정 & 이유(why): 문서는 제출용 산출물이라 **"코드가 진실원천, 문서는 그와 일치"**가 원칙. 최근 SEAT 제보·ADR-0006 추가가 여러 문서에 드리프트를 낳아, 단일 리뷰어보다 4축 병렬 감사로 누락을 촘촘히 잡음. 비밀/개인정보 유출 0건 재확인(공개 URL·SSM 경로명만).
- 관련: 감사 wf_dc846959, HANDOFF ▶세션 인계, docs/adr/*

### 2026-07-04 — survival_score(P3) 구현: 간판 "실시간 UGC 시공간 스코어링" 완성 — ADR-0007
- 한 일: 콘솔·자격증명 없이 가능한 최우선(HANDOFF ▶세션 인계 지목)인 **survival_score를 풀스택으로 완성**. 백엔드(SQL 시공간 신호 뷰 + 순수 함수 조립 + 스코어드 반경/bounds/단건 API) + 프론트(마커 3색·리스트/상세 상태 배지 — 예약 슬롯 채움). 간판 헤드라인의 미구현분(지리검색·제보는 라이브였으나 점수 자체가 없었음)을 채웠다.
- 결정 & 이유(why) — 상세는 [ADR-0007](docs/adr/0007-survival-score-sql-signals-java-compose.md):
  - **하이브리드 계산(시공간 집계=SQL 뷰, 가중치 조립·등급=순수 함수)**: CLAUDE.md §5 "시공간 랭킹은 DB에서" 준수(장소별 제보 최근성/신뢰도 집계를 `place_report_signals` 뷰가 계산 → 전체스캔·N+1 회피). 최종 `0.25·distance+0.20·comfort+0.20·freshness−0.15·risk`와 등급 분기는 **DB 없이 8개 단위테스트되는** `SurvivalScore` 순수 함수로. 튜닝 잦은 "정책"은 함수, 무거운 집계는 SQL — 역할 분리.
  - **트렌드 근거(2026-07 웹 확인)**: 시간감쇠 스코어링의 정설 트레이드오프가 "**SQL 레이어=대용량 성능 / 앱 레이어=복잡·유연 로직**". 본 설계가 그 절충의 표준형 → 근거로 방어 가능. (출처: julesjacobs 지수감쇠 likes, Tacnode Data Freshness vs Latency 2025, Crunchy Data PostGIS 인덱싱.)
  - **결측 성분은 지어내지 않고 재정규화**: open_hours(운영시간)·place_features가 실데이터에 사실상 결측 → open_now 성분 제외 후 가용 가중치 재정규화(가짜 0.5 주입 거부). 데이터 붙으면 가중치 복원만으로 additive 확장.
  - **거리 의미 분리**: 마커(bounds)·단건은 거리 성분을 빼고 "장소 자체가 지금 좋은가", 반경/최근접만 거리 0.25 넣어 "지금 갈만함" — 뷰포트 마커에 거리 착시를 넣지 않음.
  - **3색 등급, 빨강 없음**: 유효제보 0 → UNKNOWN(회색·정보 부족, 제보 없는 46k 화장실의 정직한 기본값). 있으면 ≥60 GOOD(초록)/그외 OKAY(노랑). 침수도 노랑"주의"로(§6 공포 조장 금지, 빨강 버킷 거부).
  - **신뢰도·후기 제약 반영**: 뷰에 trust 가중을 미리 심어 P2 로그인 붙으면 코드 변경 없이 반영. 후기(review)는 파이프라인에서 분리(§5 휘발성 상태 ≠ 영구 평판).
- 검증: 로컬은 Docker(colima)의 docker-java API 협상 이슈로 Testcontainers IT가 skip → **postgis 컨테이너에 실제 V1~V4 마이그레이션을 적용하고 뷰+스코어드 반경 쿼리를 시나리오별(제보없음/신선긍정/신선부정/만료제외)로 직접 실행해 시맨틱 검증**(캡 1.0·만료 WHERE·신뢰도0.7·severity·COALESCE·거리 모두 확인). 순수 함수 8건·컨트롤러 테스트는 로컬 green. 실 IT(엔드투엔드 5건, 등급 전이·만료 제외)는 표준 Docker인 CI에서 검증(머지 게이트). 프론트 typecheck·lint·build green.
- 산출물: 백엔드 `V4__place_report_signals_view.sql`·`SurvivalScore`·`ScoredPlaceView`·`dto/SurvivalScoreResponse`·`PlaceRepository`(스코어드 3쿼리)·`PlaceSearchService`·`PlaceResponse`, 테스트 `SurvivalScoreTest`(8)·`SurvivalScoreIT`(5)·`PlaceSpatialQueryIT`(스코어드 리포인트+신호0 검증). 프론트 `lib/survival.ts`·`marker.ts`(등급 3색 링)·`KakaoMapLive`·`PlaceRow`·`PlaceDetailOverlay`·`types/place.ts`.
- 다음: OAuth 콘솔(사용자) → 로그인/후기/trust로 점수 신뢰도 강화 · 추천 시나리오(`/recommendations`)는 survival_score에 시나리오 가중을 얹는 자연스러운 다음 조각 · 날씨(open_now/기온)로 결측 성분 복원.

### 2026-07-05 — 추천(/recommendations, P3) 라이브: survival_score에 시나리오 가중을 얹은 정식 랭킹 — ADR-0008
- 한 일: HANDOFF ▶세션 인계가 지목한 "콘솔 없이 바로 가능한 다음"인 **추천 시나리오를 풀스택으로 완성**. 백엔드 `GET /recommendations?scenario=rest30|restroom|rain`(2단 검색: 공간 인덱스 선필터 → 시나리오 가중 재랭킹) + 프론트 "급해요"를 nearest 팬아웃 근사에서 **survival_score 정식 랭킹으로 승격**(reason 근거 표시).
- 결정 & 이유(why) — 상세는 [ADR-0008](docs/adr/0008-recommendations-scenario-weighted-ranking.md):
  - **survival_score 순수 함수 재사용, 가중치만 파라미터화**: `SurvivalScore.of(Weights, …)` 오버로드 추가 — 조립식(가중평균 base − risk 페널티)·등급 규칙은 지도 배지와 **완전히 동일**하고 시나리오별로 가중치만 바꾼다. 스코어링 정책이 한 곳에 모여 튜닝·테스트 안전(ADR-0007과 같은 논리). 시나리오마다 별도 공식(중복)·SQL CASE 랭킹(정책을 쿼리에 매몰) 거부.
  - **시나리오 = (카테고리 집합, 가중치)**: rest30 comfort↑(0.35, 시원하게 오래 앉을 곳) / restroom distance 압도(0.60, 급함 → 가까운 무제보 화장실이 먼 유제보를 이김) / rain risk↑(0.40, 침수·미끄럼 회피). 카테고리·가중치를 백엔드 enum이 소유(단일 진실), 프론트 SCENARIO_META는 표시만.
  - **2단 검색(retrieval → re-rank)**: PostGIS 공간 인덱스(ST_DWithin geography + KNN)가 카테고리 안 후보 풀(limit×5, [50,200])을 선필터 → 앱이 matchScore로 재랭킹. 거리순 선필터가 "가깝지만 붐빔"을 올려도 재랭킹이 뒤집을 재료 확보, 상한이 재랭킹 비용 고정. 조밀한 화장실(46k)도 pool로 후보 충분.
  - **트렌드 근거(2026-07 웹 확인)**: retrieval → context-aware re-ranking(coarse top-K 후보 → 재랭커 재점수)은 검색 랭킹의 표준 2단 구조. 본 설계가 그대로. (출처: EmergentMind Contextual Retrieval & Context-Aware Ranking, Crunchy Data PostGIS 인덱싱.)
  - **두 점수 분리(의도)**: `place.survival`=§5 표준 "이 장소 지금 상태"(지도와 동일 배지, 어디서나 일관) / `matchScore`=시나리오 "이 상황 적합도"(정렬 기준) + `reason`(제보 요약). 단일 점수로 정렬하면 배지가 시나리오마다 바뀌어 지도와 달라 보임.
  - **rain risk 0.40은 §6와 양립**: 지도 배지는 §6("공포 조장 금지")대로 risk −0.15로 순화 유지. 비 피난은 사용자가 **명시적으로** 침수를 피하는 상황이라 랭킹에서 젖은 곳을 적극 강등(순위 하향 ≠ 빨간 경고 라벨).
- 검증: 단위테스트 15건 로컬 green — `RecommendationScenarioTest`(6, 가중치가 의도한 랭킹: restroom 근접우선/rest30 comfort우선/rain 침수강등/CSV/대소문자/미지원400), `RecommendationReasonTest`(4), `RecommendationControllerTest`(5, 검증·기본값·클램프·시나리오매핑). `SurvivalScoreTest`(8) 회귀 없음(default 가중치 불변). 엔드투엔드 IT 3건(`RecommendationIT`: 근접우선+matchScore·survival 동반 / 카테고리 밖 제외 / rain 침수 강등)은 colima 이슈로 로컬 skip → 표준 Docker CI 검증(머지 게이트). 프론트 typecheck·lint·build green. JaCoCo 0.35 floor 통과.
- 산출물: 백엔드 `domain/recommend/`(RecommendationController·Service·Scenario·Reason + dto/RecommendationResponse)·`SurvivalScore.Weights` 오버로드·`PlaceRepository.findWithinRadiusScoredByCategories`(카테고리 집합 IN 필터, 인덱스 경로 유지). 프론트 `app/api/urgent/route.ts`(→/recommendations 프록시)·`types/place.ts`(matchScore·reason)·`lib/categories.ts`(SCENARIO_META 표시만)·`components/urgent/UrgentResults.tsx`(reason 표시)·`urgent/page.tsx` 카피. `docs/adr/0008`.
- 관련: PR #24(`4136428`), [ADR-0008](docs/adr/0008-recommendations-scenario-weighted-ranking.md), 태스크데프 rev19(라이브 배포·프로덕션 실측), `domain/recommend/*`·`RecommendationIT`.
- 다음: OAuth 콘솔(사용자) → 로그인/후기/trust · 날씨 API(open_now/기온 결측 복원) → 추천 open_now 성분도 additive 복원 · AI 한줄 요약(Claude, 곁다리).

### 2026-07-05 — 추천 라이브 반영: 전 문서 정합 감사(다중 에이전트) 8건 수정
- 한 일: `/recommendations`(PR #24) 머지·배포 후 전 문서를 4축 병렬 읽기전용 감사 에이전트로 코드·라이브 상태와 대조 → 확증 드리프트 8건 수정(수정은 중앙에서 일괄 적용, 커밋 `50cc30d`).
- 수정 요지(전부 코드/라이브 실측 대조): HANDOFF 삭제된 브랜치 토큰(feat/p3-*)→PR #23/#24·라이브 태스크데프 rev17→**rev19**(AWS describe 확인)·survival_score/추천 "구현"→"라이브"·최종 갱신일·프로덕션 실측 근거 / README P3 추천 라이브 마일스톤 불릿 추가 / frontend/README "급해요=nearest 팬아웃·병합"→"백엔드 /recommendations 프록시" / design-brief 급해요 탭 서술(거리순 kNN→시나리오 가중 랭킹·reason)+API 계약에 /recommendations 추가 / WORKLOG 07-05 엔트리 관련(PR·commit·rev) 트레이스 라인.
- 결정 & 이유(why): 문서는 제출용 산출물이라 **"코드가 진실원천, 문서는 그와 일치"**. 새 기능 머지가 7개 문서에 드리프트를 낳아, 단일 리뷰어보다 4축 병렬 감사로 촘촘히 잡음(2026-07-04 감사와 동일 방식). **감사 확인·수정 불요**: ADR-0008 가중치/카테고리 표가 `RecommendationScenario.java`와 정확히 일치, CLAUDE.md 스펙 정합, TROUBLESHOOTING(TS-001~009 연속)·DEPLOY 무드리프트, WORKLOG 테스트 카운트 산술(단위15·IT3·SurvivalScore8 회귀0) 정확. 비밀/개인정보 유출 0건(gitleaks green).
- 관련: 커밋 `50cc30d`, 감사 에이전트 4축(CLAUDE/README · HANDOFF · ADR/design-brief · WORKLOG/TS/DEPLOY)

### 2026-07-05 — 코드·문서 품질 하드닝: 3축 심층 리뷰 + README 재작성 + 문서 톤 중립화
- 한 일: 레포 전체(코드+문서+표현)를 "처음 읽는 사람이 한 줄씩 정독한다"는 기준으로 3축 병렬 읽기전용 리뷰(백엔드 코드 / 프론트 코드 / GitHub 표현·문서) → 확증 개선만 반영. (1) 코드 품질 PR #25, (2) 문서 표현·구조 하드닝.
- 코드(PR #25, 두 코드베이스 모두 "crash 버그 없음·데드코드 없음"으로 평가된 위에서의 개선):
  - fix: `RecommendationReason`이 부정 제보만 있는 곳을 "좋은 제보"로 오표기하던 결함(긍정 comfort 없으면 중립 "최근 제보 n건") + 프론트 최근제보 로딩 실패를 "제보 없음"으로 뭉개던 것(에러 분기) + 제보 성공 시 survival 반영되게 place·places 쿼리 무효화.
  - refactor: 컨트롤러 복붙 검증을 `ApiRequests`로 일원화 · 아이콘 이름 `IconName` 타입으로 컴파일 타임 검증(satisfies) · 미사용 필드 제거 · ingest 카운터 보존(RuntimeException 집계) · a11y(aria-label) · Dockerfile 스택 표기 교정.
- 문서(표현·구조):
  - **README 재작성** — 내부 changelog 톤 → 제품/아키텍처 README로: 라이브 링크·CI 배지, 무엇/왜(핵심 차별점), **ASCII 아키텍처 다이어그램**, **survival_score 공식+작동 원리**, **로컬 빠른 시작(docker compose→gradlew→pnpm)**, /recommendations 포함 API, 문서 색인, License. 구현 이력은 `<details>`로 접음. (기존엔 README가 사람을 CLAUDE.md로 보냈음 → 자체완결화.)
  - **ADR 색인**(`docs/adr/README.md`) 신설 — 0001~0008 한 줄 요약 표.
  - **LICENSE(MIT)** 추가 · `.editorconfig`(Java 4 / TS 2) 추가.
  - **문서 톤 중립화** — CLAUDE.md/WORKLOG/TROUBLESHOOTING의 노골적 "면접·포트폴리오" 메타 문구를 중립 개발 문서 톤으로(의사결정 근거·트렌드 확인·why 기록 규율은 유지). 회사/전략 메모는 이미 `.local/PORTFOLIO-CONTEXT.md`(gitignore).
- 결정 & 이유(why): 리뷰 지적을 그대로 반영하지 않고 "실제 개선인가/리스크 없는가"로 취사선택(라이브 프로덕션이라 위험한 리팩터는 배제). 문서 톤 중립화·LICENSE·다이어그램·로컬 실행법은 처음 보는 사람의 이해·신뢰를 높이는 표준 정비. 코드 변경은 백엔드 test+coverage·프론트 typecheck/lint/build green + CI 실 PostGIS IT 통과 후 머지(PR #25), 프로덕션 배포 확인.
- 관련: PR #25, 리뷰 에이전트 3축, `docs/adr/README.md`·`LICENSE`·`.editorconfig`, 사용자 결정(LICENSE=MIT·문서 중립화)

### 2026-07-09 — 날씨 API(기상청 초단기실황) + Redis TTL 캐시 (P3, 1부: 백엔드+인프라)
- 한 일: HANDOFF ▶세션 인계가 지목한 "콘솔 없이 바로 가능한 다음"인 **날씨**를 착수. 좌표→기상청 격자 변환, 초단기실황 조회 클라이언트, Redis TTL 캐시, `GET /weather?lat=&lng=` 엔드포인트, ElastiCache Terraform까지. (survival_score 기온 성분 복원은 2부에서 additive하게 — 이 커밋은 데이터 소스+캐시+노출.)
- 결정 & 이유(why):
  - **소스 = 초단기실황(getUltraSrtNcst), 예보(Fcst) 아님**: 앱 테제가 "지금 상태"라 **관측 기온·습도·강수(T1H/REH/RN1/PTY)** 가 survival_score 기온 성분에 더 정확하다. 예보는 향후 "곧 비 옴" 알림(P4/심화)용으로 남긴다. CLAUDE.md §4/§7 문구는 "초단기예보"였으나, 목적(=지금 체감 기온 복원)엔 실황이 맞아 정제 선택(범위 확장 아님).
  - **캐시 백엔드 = ElastiCache Redis (사용자 결정)**: 인프로세스(Caffeine) 대신. CLAUDE.md §7 스펙 정합 + "캐시 전략" 포트폴리오 포인트 실증 + 분산 캐시(다중 태스크 공유). `cache.t3.micro`(신규계정 프리티어 750h/월·12개월 대상), 사설 서브넷 + SG로 ECS만 6379 접근.
  - **캐시 경계 = 외부 HTTP 호출(@Cacheable "weather")**: 키=`nx:ny:baseDate:baseTime`. 발표시각이 매시각 바뀌어 키가 자연 회전하고, 같은 슬롯 재조회를 막아 기상청 rate limit을 아낀다. TTL 30분. 빈 결과(장애)는 `unless`+`disableCachingNullValues`로 미캐시(일시 장애가 굳지 않게).
  - **가용성을 캐시에 결합하지 않음(CacheErrorHandler)**: Redis 장애·미프로비저닝이어도 캐시 오류를 삼키고 기상청을 직접 호출. 나아가 **ALB 헬스체크에 Redis를 넣지 않는다**(`management.health.redis.enabled: false` 유지) — 캐시는 non-critical 부가 계층이라 Redis 블립이 태스크를 죽이면 안 됨. (HANDOFF의 "헬스체크 재활성" 메모를 이 근거로 대체: 재활성은 가용성 저하 위험이라 의도적으로 하지 않음.)
  - **격자 변환은 순수 함수 + 기준값 테스트**: 기상청 DFS_XY_CONV 공식을 그대로. 서울(60,127)·부산(98,76)·상도(59,125)로 못 박음(조용한 오답 방지).
  - **data.go.kr 이중 인코딩 함정 회피**: serviceKey는 **디코딩 키**를 주입받아 UriBuilder가 한 번만 인코딩. (참고: 날씨 API도 data.go.kr serviceKey가 필요 — HANDOFF "콘솔 불필요"는 정정. §⑤ 공부공간 데이터와 같은 계정 키로 커버.)
  - **트렌드 근거(2026-07 웹 확인)**: KMA getUltraSrtNcst 엔드포인트·요청변수(serviceKey/base_date/base_time/nx/ny) 현행 확인. Spring Boot 4(Framework 7) 캐시 추상화는 Redis 스타터+Lettuce 유지, `RedisCacheManager` 빈 직접 정의 + JSON 직렬화가 표준. (출처: data.go.kr 15084084, Spring Data Redis Cache 레퍼런스, Baeldung Redis Cache.)
- 검증: 유닛 12건 로컬 green — `KmaGridTest`(3, 기준 좌표), `WeatherClientTest`(5, 실 기상청 JSON 파싱·RN1 "강수없음"→0·PTY·오류코드/HTTP오류/무키 empty), `WeatherServiceTest`(2, KST −40분 정시 내림 + 날짜 롤오버). 전체 `*Test` 회귀 0. `terraform validate` green. **엔드투엔드(실 기상청 호출)·Redis 왕복은 serviceKey 확보 + ElastiCache apply 후 실측**(대기).
- 산출물: 백엔드 `domain/weather/`(KmaGrid·PrecipitationType·Weather·WeatherClient·WeatherService·WeatherController + dto/WeatherResponse)·`global/config/RedisCacheConfig`·`application.yml`(weather.service-key). 인프라 `infra/terraform/elasticache.tf`·`ssm.tf`(kma_service_key)·`variables.tf`·`outputs.tf`·`ecs.tf`(REDIS_HOST env + KMA_SERVICE_KEY secret, 문서화용).
- 다음(배포 절차): ① 사용자가 data.go.kr serviceKey 확보(기상청 15084084 활용신청) → `.local`. ② `TF_VAR_kma_service_key=… terraform apply`(ElastiCache+SSM, 비용 확인 후). ③ 라이브 태스크데프에 REDIS_HOST(=redis_endpoint output)+KMA_SERVICE_KEY(SSM) 심은 새 rev 등록 + `update-service --force-new-deployment`(proxy-secret rev13과 동일 패턴). ④ `/weather?lat=&lng=` 실측. 이후 **2부**: survival_score 기온 성분 additive 복원(SurvivalScore.Weights 확장, ADR + IT).
- 관련: 브랜치 `feat/weather-kma-redis`, `domain/weather/*`, `RedisCacheConfig`, `elasticache.tf`, [TS-010](TROUBLESHOOTING.md) (Boot 4 Redis 직렬화·커스터마이저 이관).

### 2026-07-09 — 소셜 로그인(카카오/구글 OAuth2) + JWT 세션 (P2, 백엔드)
- 한 일: 후기·trust_score의 선행인 **소셜 로그인 백엔드**를 완성. 카카오/구글 인가코드 서버 교환 → 사용자 upsert → JWT 발급, `POST /auth/kakao`·`POST /auth/google`·`GET /me`. 사용자가 카카오(REST 키+Redirect URI)·구글(OAuth 웹 클라이언트+테스트유저) 콘솔을 준비해줘서 착수(키는 `.local`·SSM만).
- 결정 & 이유(why):
  - **BFF code 교환 방식(oauth2-client 스타터 미사용)**: 등록된 redirect URI가 프론트 BFF(Vercel `/api/auth/{provider}/callback`)라, 리다이렉트는 프론트가 받고 백엔드는 code만 받아 서버에서 토큰 교환(RestClient). 기존 BFF 아키텍처(ADR-0004)와 정합. Spring `oauth2-client`의 서버측 리다이렉트 흐름은 이 구조와 안 맞아 배제 — security 코어(필터체인·JWT 검증)만 씀. redirectUri는 로컬/프로덕션이 달라 프론트가 자기 콜백 URL을 요청 바디로 넘겨 토큰 교환 시 일치시킨다.
  - **JWT(jjwt 0.13, HS256) 무상태 세션**: 서버가 세션 상태를 안 들고 서명으로 신원 증명. sub=userId, role 클레임, 기본 7일. mp 레퍼런스와 정렬. (트렌드 근거 2026-07: jjwt 0.13이 최신, Spring Security 6~7 호환. 출처: jwtk/jjwt GitHub, mvnrepository.)
  - **시크릿 지연 검증(배포 안전성)**: JWT_SECRET이 없어도 컨텍스트는 뜨고, 발급/검증 시점에 없거나 <32바이트면 명확히 실패. 값 주입 전 배포되어도 앱이 안 죽음(날씨 CacheErrorHandler와 동일 원칙).
  - **라이브 앱 보안 최소침습**: Spring Security 신규 도입이지만 SecurityConfig는 `/me`만 인증 요구, `anyRequest().permitAll()`. 기존 공개 엔드포인트(/places·/reports·/recommendations·/actuator·swagger)를 하나도 안 막는다(default-permit). 로그인 필요한 신규 기능(후기)은 그때 matcher 추가. 미인증 보호경로는 401(리다이렉트 아님 — API). CSRF off·STATELESS.
  - **(provider, provider_id) upsert**: users 테이블은 V2에서 이미 생성됨 → 엔티티 매핑만(ddl-auto=validate, 마이그레이션 불필요). 재로그인 시 프로필(닉/이미지/이메일) 최신화, trust/role 불변. 카카오 이메일은 비즈인증 없으면 미제공이라 provider_id로 식별(email nullable). 신규 유저 trust_score=0(익명과 동일 기저에서 시작, V4 뷰 0.7+0.3*min(trust/100,1)).
  - **응답 record는 Jackson 3 기준**(TS-004): 카카오/구글 응답을 타입 record로 역직렬화.
- 검증: 유닛 11건 로컬 green — `JwtServiceTest`(4, 라운드트립·만료거부·서명불일치거부·무키예외), `KakaoOAuthClientTest`(2, code교환+id/닉/이미지 매핑·이메일null / 4xx→401), `GoogleOAuthClientTest`(1, sub/email/name/picture), `AuthServiceTest`(4, 신규생성/기존갱신 dirty checking/기본닉네임/미지원404). 전체 `*Test` 회귀 0(기존 컨트롤러 슬라이스 안 깨짐). 실 DB upsert·풀컨텍스트(시큐리티 배선)·`/me` 401/200은 CI IT + 배포 후 실측 대기(colima 로컬 IT skip).
- 산출물: 백엔드 `domain/auth/`(User·UserRepository·AuthProvider·Role·JwtService·AuthService·AuthController + oauth/{OAuthClient·KakaoOAuthClient·GoogleOAuthClient·OAuthUserInfo} + dto/{LoginRequest·AuthResponse·UserResponse})·`global/security/`(SecurityConfig·JwtAuthenticationFilter)·`build.gradle`(security+jjwt)·`application.yml`(kakao.oauth·google.oauth·jwt).
- 다음(배포 절차, 날씨 인프라와 함께 통합 apply — ecs.tf 충돌 방지로 코드 브랜치엔 tf 미포함):
  1. SSM SecureString: `/geuneul/kakao_rest_api_key`(로그인 client_id), `/geuneul/google_client_id`, `/geuneul/google_client_secret`, `/geuneul/jwt_secret`(`.local/oauth.env`에 생성 완료). 카카오 client_secret은 미사용(빈값, SSM 생략).
  2. 라이브 태스크데프에 위 4개 secret 추가한 새 rev 등록 + `update-service --force-new-deployment`(proxy-secret rev13 패턴).
  3. 그다음 **프론트**: 로그인 버튼 → 제공자 authorize 리다이렉트 → BFF 콜백(`/api/auth/{provider}/callback`)이 code를 `/auth/{provider}`로 프록시 → JWT를 httpOnly 쿠키로 → `/me`.
  4. 이후 **후기(review)**·trust_score(로그인 제보 가중, V4 뷰가 이미 준비됨).
- 관련: 브랜치 `feat/oauth-jwt`, `domain/auth/*`, `global/security/*`, CLAUDE.md §9(인증 API), ERD §8(users). 스택 근거 jjwt 0.13(jwtk/jjwt).

### 2026-07-09 — 프론트 로그인 플로우 (OAuth end-to-end, feat/oauth-jwt)
- 한 일: 백엔드 OAuth에 이어 **프론트 로그인 UI + BFF 콜백**을 붙여 소셜 로그인을 end-to-end 완성. 로그인 버튼(카카오/구글) → 제공자 authorize 리다이렉트 → BFF 콜백이 code를 백엔드로 교환 → JWT를 httpOnly 쿠키로 → `/me`. MVP에 없던 **"내 정보" 탭 신설**.
- 결정 & 이유(why):
  - **"내 정보" 탭 신설(기존 3화면 무침습)**: 전역 헤더가 없어서 로그인 버튼 자리가 없었다. 지도/급해요/제보 화면을 건드리지 않고 TabBar에 4번째 탭 + `/mypage`를 추가 — 모바일 관용(당근/카카오식 "내정보" 탭)이고 로그인이 "살"이라 주 화면을 침범하지 않는다.
  - **BFF에서만 쿠키 처리(기존 프록시는 쿠키 무시)**: authorize 시작(`GET /api/auth/{provider}`)은 CSRF state를 httpOnly 쿠키에 심고 302, 콜백(`/api/auth/{provider}/callback`)은 state 검증 후 백엔드 `/auth/{provider}`로 code 교환하고 **JWT를 httpOnly 쿠키(geuneul_session)로** 세팅. `/api/me`가 쿠키의 JWT를 Bearer로 백엔드에 전달(브라우저 JS는 httpOnly라 토큰을 못 봄 = XSS 토큰 탈취 방어). 기존 proxy/proxyPost는 Set-Cookie를 안 넘겨 재사용 불가라 콜백 전용 로직 신설.
  - **client_secret은 프론트에 두지 않음**: authorize URL엔 client_id만 필요(서버 전용 env). 토큰 교환(secret)은 백엔드가 수행 → 시크릿이 프론트 번들·네트워크에 안 뜬다.
  - **로그인 버튼은 `<a>`(next/link 아님)**: OAuth 시작은 브라우저 전체 이동이어야 하고 Link의 prefetch가 인가 라우트를 잘못 트리거하므로 의도적으로 `<a>` + eslint-disable(사유 주석).
  - **CSRF state**: authorize→callback 왕복을 httpOnly state 쿠키(10분)로 검증(불일치/누락 시 로그인 거부).
  - **redirectUri는 프론트가 자기 콜백 URL을 계산해 넘김**: 로컬(http://localhost:3000)·프로덕션(https://geuneul.vercel.app)이 달라 토큰 교환 시 정확히 일치해야 하므로 요청 host/proto로 도출.
- 검증: 프론트 `typecheck`·`lint`·`build` 전부 green. 라우트 등록 확인(`/api/auth/[provider]`·`/callback`·`/logout`·`/api/me`·`/mypage`). 실제 로그인 왕복은 백엔드 배포(키 주입) 후 실측.
- 산출물: 프론트 `lib/auth.ts`(provider·쿠키·authorize URL)·`app/api/auth/[provider]/route.ts`·`.../callback/route.ts`·`app/api/auth/logout/route.ts`·`app/api/me/route.ts`·`lib/api.ts`(fetchMe·logout)·`lib/queries.ts`(useMe·useLogout)·`components/shell/TabBar.tsx`(내정보 탭)·`lib/icon-paths.ts`(user 아이콘)·`app/(shell)/mypage/page.tsx`·`.env.example`(KAKAO_REST_API_KEY·GOOGLE_CLIENT_ID).
- 배포 시 프론트 env(Vercel): `KAKAO_REST_API_KEY`(로그인 client_id)·`GOOGLE_CLIENT_ID` 추가(서버 전용). 콘솔 Redirect URI는 이미 `.../api/auth/{provider}/callback` 등록됨.

### 2026-07-09 — 배포: 날씨(P3)·소셜 로그인(P2) 프로덕션 라이브 + 캐시 하드닝 2건
- 한 일: 브랜치 3개(날씨 #26·OAuth #27·핫픽스)를 PR→CI(실 PostGIS IT green)→머지→**프로덕션 배포**. ElastiCache Redis + SSM 5종 `terraform apply`(8 add/0 change/0 destroy) → 라이브 태스크데프에 REDIS_HOST+secret 5종 배선(rev23) → 이미지 배포(rev24/25). Vercel env(KAKAO_REST_API_KEY·GOOGLE_CLIENT_ID) 추가 + 재배포.
- 라이브 실측: `/weather` 광화문 `지금 23°C, 비`(기온·습도·강수·PTY 전부)·부산 `31°C`·상도동, **캐시 히트 정상**(동일값·에러0·Redis 경고0). 로그인 authorize 리다이렉트(카카오/구글 client_id·redirect_uri·state 정확)·`/me` 401. health UP. 기존 공개 API 회귀 0.
- **라이브에서만 드러난 캐시 버그 2건(유닛테스트 사각 → 실측이 최종 게이트):**
  - **TS-011**: `@Cacheable(unless="…#result.isEmpty()")` — Spring이 Optional을 언랩해 #result=Weather라 present일 때 SpEL `isEmpty()` 오류→500(키 없던 경로에선 안 드러남). `unless="#result == null"`로 교정 + 캐시 프록시를 태우는 `WeatherCacheProxyTest`.
  - **TS-012**: `GenericJacksonJsonRedisSerializer.builder().build()`(Jackson3판)이 무타이핑→@class 없이 저장→GET 시 LinkedHashMap 캐스트 실패(캐시 히트 500). `JacksonJsonRedisSerializer<>(Weather.class)`(타입 바인드)로 교체 + 직렬화 왕복 `RedisCacheConfigTest`.
- 결정 & 이유(why): ① 캐시 백엔드 ElastiCache(사용자 결정) — 프리티어 대상 + graceful degradation(지워도 날씨는 직접호출로 동작)이라 "완벽 동작→캡처→무료화" 요구와 양립. ② 태스크데프 config는 `ignore_changes`라 라이브 rev 수동 배선(describe→env/secret 추가→register→update-service, proxy-secret rev13 패턴). ③ 인프라 tf는 코드 브랜치와 분리해 통합 커밋(ecs.tf 충돌 방지). ④ 두 캐시 버그는 "프록시/직렬화로 동작하는 기능은 프록시·직렬화를 실제로 태우는 테스트가 필요"의 사례 — 회귀 테스트로 고정.
- 관련: PR #26·#27·#28·#29, TS-011·TS-012, `elasticache.tf`·`ssm.tf`·`ecs.tf`, 라이브 rev25. 배포 접근: AWS CLI(geuneul-admin)·Vercel CLI(akftjdwn-9388) 로컬 인증.

### 2026-07-09 — 소셜 로그인 실사용 검증 완료(구글·카카오) + 카카오 콘솔 3중 함정 해소
- 한 일: 배포된 OAuth를 브라우저에서 실제 로그인까지 검증. **구글은 첫 시도에 프로필까지 성공**. **카카오는 콘솔 설정 3건 해소 후 성공**(TS-013): (A) 로그인 Redirect URI를 [고급]의 로그아웃 칸이 아닌 [플랫폼 키]>[REST API 키]의 로그인 칸에 정확히 등록(KOE006 해소), (B) 호출 허용 IP 127.0.0.1 제거, (C) Client Secret 활성화 ON이라 백엔드에 SSM 배선 — 스크린샷 시크릿의 I/l 오독 정정 후 KOE010 해소. → 라이브 rev26.
- 결정 & 이유(why): Client Secret은 **ON 유지 + 백엔드 배선**(OFF보다 보안·포트폴리오 우위). 정정은 SSM 값 갱신 + `force-new-deployment`(ECS secret은 태스크 시작 시 주입). "구글이 같은 코드로 되면 카카오 실패는 콘솔 문제"로 범위를 좁혀 코드 무변경으로 해결.
- 검증(프로덕션 실사용): 구글 로그인 → 프로필(가_홍성주…·Google·이메일·신뢰도0), 카카오 로그인 → 프로필(홍성주·카카오·신뢰도0). 로그인 P2 **실질 완성**.
- 산출물: SSM `kakao_client_secret`(rev26), TS-013. 코드 변경 없음(콘솔·시크릿만).
- 다음(새 세션): ① 날씨 2부(survival_score 기온 반영) ② 후기(review) 백엔드+trust_score ③ 공부공간 데이터 적재. 상세는 HANDOFF ▶세션 인계.

### 2026-07-09 — 날씨 2부: survival_score comfort에 기온(체감) additive 복원 (P3, ADR-0009)
- 한 일: HANDOFF "콘솔 없이 바로 가능한 다음 ①"인 날씨 2부 착수. P3 날씨 1부(라이브)가 준 `Weather`(기온·습도·강수)를 survival_score의 comfort_score 안에 **additive로** 붙였다 — §5 최상위 가중치 구조(distance/comfort/freshness/risk)는 안 건드리고 comfort 성분의 내부 조립만 바꿨다.
- 결정 & 이유(why):
  - **체감온도 공식 = 기상청 2022-06-02 개정 여름철 공식 그대로(습구온도 Stull 근사)**: 웹 검색(2026-07)으로 확인. 우리 `Weather`가 이미 가진 T1H(기온)·REH(습도)만으로 계산 가능해(풍속 불필요) 추가 데이터 소스 없이 적용. NWS Rothfusz 회귀(화씨·RH 극단 보정 다항식)는 검토했으나 관측 소스가 애초에 기상청이라 같은 기관 공식을 그대로 쓰는 게 더 방어 가능해 기각.
  - **comfort 매핑 앵커 = 2026 기상청 폭염특보 체감온도 임계값(33/35/38도)**: 마찬가지로 웹 검색 확인. 2026년 신설된 "폭염중대경보"(체감 38도)까지 앵커에 반영 — 오케스트레이터 지시 예시값(31도)보다 **공식 기준선**이 더 방어 가능해 그쪽을 채택하고 이유를 ADR에 명시.
  - **comfort_score 내부 서브조립 = 제보(0.6)·날씨(0.4) 가중평균**: UGC 제보는 "이 장소 자체"의 증거(에어컨·그늘)라 우선하고, 날씨는 지역 평균이라 보조 신호로만. weatherComfort=null(폴백)이면 기존과 100% 동일 — 4-인자 오버로드는 그대로 두고 5-인자 오버로드를 추가해 기존 호출부·테스트 무회귀.
  - **날씨 주입 = 서비스 레이어에서 요청당 1회, N+1 절대 금지**: `WeatherService.getComfortScore(lat,lng)`(신규, getWeather+HeatComfort 위임)를 PlaceSearchService(반경=요청좌표/bounds=centroid/단건=그 장소좌표)·RecommendationService(요청좌표) 각각에서 딱 한 번만 불러 결과 배치 전체에 공통 적용. Mockito 단위테스트로 "결과 건수 무관 1회 호출"을 못 박았다(KMA rate limit 보호, 지역 신호라는 성질에도 부합).
  - **강수 페널티는 균일(-0.15)만, 카테고리별 실내 선호 가중은 이번 스코프 제외**: `place_features`에 실내/실외 플래그가 없어 정밀 반영 불가 — 지어내지 않고(§0-B) 확장점으로 ADR에 기록.
  - **등급(UNKNOWN/GOOD/OKAY)은 여전히 reportCount로만 결정**: 날씨가 좋아도 제보 0건 장소가 GOOD으로 승격되지 않게 — ADR-0007 등급 규칙 불변.
- 검증: 유닛 43건 로컬 green(기존 전체 + 신규 `HeatComfortTest` 9·`SurvivalScoreTest` +5·`WeatherServiceTest` +2·`PlaceSearchServiceTest` 5·`RecommendationServiceTest` 2). 체감온도·comfort 매핑 수치는 Python으로 선검증 후 테스트 값 확정(23/55→comfort 1.0, 33/70→0.266, 37/90→체감 40.16→comfort 0.0). IT(`WeatherComfortIT`, `@MockitoBean WeatherClient`로 KMA 네트워크 없이 관측값 주입해 `/places/{id}` 응답까지 관통 검증 — 폴백/쾌적/폭염/UNKNOWN 불변 4건)는 **로컬 colima Docker 미가용으로 skip**(XML `skipped="4"`, TS-009와 동일 — SKIP≠통과, CI green으로 최종 판정 필요).
- 산출물: 백엔드 `domain/weather/HeatComfort.java`(신규, 순수 함수)·`WeatherService.getComfortScore`(신규)·`domain/place/SurvivalScore.java`(weatherComfort 오버로드 4종 추가)·`domain/place/dto/PlaceResponse.java`(3-인자 오버로드)·`PlaceSearchService`·`domain/recommend/RecommendationService`(WeatherService 배선). 테스트 `HeatComfortTest`·`PlaceSearchServiceTest`·`RecommendationServiceTest`(신규)·`SurvivalScoreTest`·`WeatherServiceTest`(추가)·`WeatherComfortIT`(신규). 문서 `docs/adr/0009-weather-comfort-additive-restore.md`. Flyway 마이그레이션 없음(순수 계산, DB 스키마 무변경).
- 관련: 브랜치 `feat/weather-comfort-p3`, ADR-0009, ADR-0007(재정규화 원칙 계승)·ADR-0008(가중치 오버로드 재사용 패턴 계승), TS-009(로컬 SKIP≠통과 원칙 재적용).
### 2026-07-09 — 후기(review) 풀스택 (P2): 영구 평판 백엔드 + 프론트, survival_score와 완전 분리
- 한 일: HANDOFF ▶세션 인계 ②가 지목한 **후기(review)** 를 풀스택으로 완성. 백엔드 `POST /reviews`(로그인 필요, JWT)·`GET /places/{id}/reviews`(공개, 페이지네이션) + 프론트 장소 상세 "후기" 섹션(목록+별점 작성 폼, 로그인 시에만 폼 노출). reviews 테이블은 V2에 이미 있어 **신규 Flyway 불필요**(V5/V6/V7 어느 것도 안 씀).
- 결정 & 이유(why):
  - **다중 작성 정책 = 장소당 1건 upsert(재작성 시 갱신)**: 착수 전 웹으로 확인(2026-07 기준) — **구글맵은 "한 계정당 한 업체에 활성 리뷰 1건"**이 명문 정책이고(리뷰 편집·재게시로 갱신), 카카오맵은 좌표찍기·중복 별점테러 방지 쪽으로 정책을 강화 중(명시적 "1건" 문구는 확인 못 했으나 업계가 스팸/중복 억제 방향으로 수렴). 그늘의 §5도 "제보=휘발성 vs 후기=영구 평판"으로 신뢰도(trust) 가중을 전제하는데, **다중 허용은 같은 유저가 별점을 반복 투척해 평판을 부풀리는 벡터**가 되어 신뢰도 설계 취지와 어긋난다. → **업계 지배적 관행(구글) + 프로젝트 설계 취지가 같은 방향**이라 upsert로 확정. DB 유니크 제약(user_id, place_id)은 두지 않고 **서비스 레이어(`ReviewRepository.findByUserIdAndPlaceId` → 있으면 갱신/없으면 생성)로 강제** — reviews는 이미 라이브 스키마(V2)라 이번 작업만으로 새 마이그레이션을 만들지 않기로 한 범위 결정과 정합(동시 이중 제출 레이스는 로그인 필요+저빈도 UGC라 MVP 허용 리스크로 문서화, `Review.java` 클래스 주석).
  - **user_id는 JWT에서만**: `ReviewCreateRequest`엔 placeId/rating/comment/photos만 받고, 컨트롤러가 `@AuthenticationPrincipal JwtService.AuthPrincipal`에서 userId를 꺼내 서비스에 넘긴다(신원 위조 방지, ReportController와 달리 이건 로그인 전제라 principal이 항상 유효 — AuthController.me()와 동일 패턴).
  - **SecurityConfig에 POST /reviews만 좁게 보호**: `requestMatchers(HttpMethod.POST, "/reviews").authenticated()` 추가. `/me`처럼 경로 전체가 아니라 **메서드를 좁혀** GET(공개 목록)은 그대로 permitAll에 걸리게 했다(§9 API 계약대로 조회는 공개). 기존 공개 엔드포인트 무회귀(라이브 앱 원칙).
  - **rating은 엔티티에서 `short`**: reviews.rating은 V2에 `SMALLINT`로 이미 고정돼 있다. Java `int`로 매핑하면 `ddl-auto=validate`가 컬럼 타입 불일치(SMALLINT vs INTEGER)로 **부팅 시 실패**하므로(TS-004/TS-010과 같은 계열의 "실 DB로만 드러나는 타입 불일치") `short` 필드 + `int` 게터로 DTO 편의를 살렸다. 로컬 IT가 skip이라 이 판단은 스키마 문서(V2 SQL)를 직접 읽어 사전 확정 — 실제 배선은 CI가 최종 검증.
  - **작성자 조인은 네이티브 쿼리 + 인터페이스 프로젝션**: `GET /places/{id}/reviews` 응답엔 닉네임/프로필이미지가 필요한데 Review 엔티티는 (Report처럼) User와 JPA 연관관계가 없다(경량 FK-id 패턴 유지). `reviews JOIN users` 네이티브 쿼리 + `ReviewWithAuthorView` 프로젝션 인터페이스로 조인하고, countQuery를 별도 명시해 Spring Data Pageable 페이지네이션을 지원(PlaceRepository의 스코어드 쿼리와 같은 네이티브+프로젝션 패턴 재사용, DRY).
  - **photos_json은 Jackson 3 ObjectMapper로 직접 왕복**: presign이 아직 없어 URL 배열만 수용(`List<String>`, 최대 10장). Boot 4/Jackson 3(`tools.jackson.databind.ObjectMapper`, TS-004/TS-010 계열)의 `writeValueAsString`/`readValue`가 **unchecked** `JacksonException`을 던진다는 걸 jar 바이트코드로 직접 확인(`javap`)하고 try/catch 없이 작성 — 문서에 기대지 않고 실물 API를 확인하는 이 레포의 원칙(TS-010) 준수.
  - **survival_score 파이프라인 무터치**: `place_report_signals` 뷰·`SurvivalScore.java`·`ScoredPlaceView`·`PlaceRepository`의 기존 쿼리는 전혀 건드리지 않았다(§5 "후기는 survival_score와 분리"). 장소 상세(`/places/{id}`) 응답에도 후기를 끼워넣지 않고 **별도 엔드포인트**로만 제공 — 지시받은 격리 원칙 그대로.
- **트러블슈팅(TS-014, 상세는 TROUBLESHOOTING.md)**: Boot 4의 `@WebMvcTest` 슬라이스가 시큐리티 오토컨피그를 기본으로 안 끌어와 `SecurityConfig`를 `@Import`해도 `HttpSecurity` 빈이 없어 컨텍스트 로딩 실패 → `ServletWebSecurityAutoConfiguration`+`SecurityFilterAutoConfiguration`을 명시 Import해 해결(이 레포 최초의 "로그인 필요 컨트롤러" 슬라이스 테스트라 선례가 없었음).
- 검증: 백엔드 유닛 16건 로컬 green — `ReviewServiceTest`(7, upsert·JSON 왕복·404 2종·공백정규화·목록매핑, **실 Jackson 3 ObjectMapper로 photos 직렬화 계약까지 실제로 태움**) + `ReviewControllerTest`(9, 201·401×2·400×4·공개목록·size클램프, 실 SecurityConfig 필터체인 관통). 전체 백엔드 `./gradlew test jacocoTestCoverageVerification` **131건 로컬 green**(스킵 30건=Testcontainers IT, colima 이슈로 로컬 skip — TS-009 패턴, CI가 최종 게이트), 회귀 0. `ReviewFlowIT`(5, 작성→목록·upsert 행수불변·401·404·빈 목록)는 CI에서 실 PostGIS+시큐리티로 검증. 프론트 `typecheck`·`lint`·`build` 전부 로컬 green(라우트 `/api/reviews`·`/api/places/[id]/reviews` 등록 확인). gitleaks 로컬 스캔(`gitleaks protect --staged`) 무결.
- 산출물: 백엔드 `domain/review/`(Review·ReviewRepository·ReviewWithAuthorView·ReviewService·ReviewController + dto/{ReviewCreateRequest·ReviewResponse·ReviewListResponse})·`SecurityConfig`(POST /reviews 보호). 프론트 `types/review.ts`·`lib/api.ts`(fetchPlaceReviews·createReview)·`lib/queries.ts`(usePlaceReviews·useCreateReview)·`lib/backend.ts`(proxyAuthedPost — 세션쿠키→Bearer 신규 헬퍼)·`app/api/reviews/route.ts`·`app/api/places/[id]/reviews/route.ts`·`components/place/ReviewsSection.tsx`(목록+별점폼)·`components/place/PlaceDetailOverlay.tsx`(P2 플레이스홀더 교체)·`components/ui/Icon.tsx`(인스턴스별 `filled` 오버라이드)·`lib/icon-paths.ts`(star 아이콘).

### 2026-07-09 — 모더레이션(신고+검수 큐) 백엔드 (P2): `POST /flags` + `GET·POST /admin/flags/**`
- 한 일: CLAUDE.md §0-7("제보·후기는 허위·명예훼손 관리 필수, 신고/검수 큐를 처음부터 염두")이 지목한 마지막 P2 UGC 조각. `POST /flags`(로그인 필요, 제보/후기 신고)·`GET /admin/flags/pending`(ADMIN 전용, 페이지네이션+대상 요약)·`POST /admin/flags/{id}/resolve`(ADMIN 전용, PENDING→RESOLVED/DISMISSED)를 신설. Flyway **V7__flags.sql**(V5·V6은 다른 브랜치 선점이라 오케스트레이터 지시대로 V7만 사용).
- 결정 & 이유(why):
  - **스키마 = ERD 초안의 reports_flags/reviews_flags 2테이블이 아니라 통합 `flags` 테이블(target_type, target_id 다형 참조)**: 신고→검수 라이프사이클(사유·상태·처리시각)이 대상 종류와 무관하게 완전히 동일하고, 관리자 큐(`GET /admin/flags/pending`)는 제보 신고·후기 신고를 **한 화면에서 최신순으로** 봐야 하는데 분리 테이블이면 UNION 조회가 필요해진다. 신고 대상이 늘어도(예: 장소 자체) target_type 값만 추가하면 되는 확장성도 이점 — 오케스트레이터 지시의 "권장" 옵션을 그대로 채택하고 V7 주석에 근거를 남겼다. target은 다형이라 강제 FK를 걸지 않고 (target_type, target_id) 인덱스 + 서비스 레벨 존재 검증으로 대체(Report/Review의 경량 FK-id 패턴과 동일 계열).
  - **중복 신고 방지 = 사전 체크(existsBy...) + DB 유니크 제약(uq_flags_target_reporter) 이중 방어, 위반 시 409**: "멱등 or 409" 중 409를 택함 — 신고는 멱등하게 재시도할 대상이 아니라(중복 신고는 UGC 스팸 공격 벡터이기도 함) 클라이언트가 명확히 "이미 신고함"을 알아야 하는 사용자 행동이라 실패를 숨기지 않는 게 맞다고 판단. 서비스 레이어 사전 체크로 정상 경로에서 DB 예외 스택트레이스 없이 깔끔한 409를 주고, `DataIntegrityViolationException` catch를 레이스 컨디션(사전 체크~save 사이의 동시 이중 제출)의 최종 방어선으로 추가 — Review의 "동시 이중 제출은 저빈도 UGC라 리스크 허용"보다 한 단계 더 방어적인 이유는 신고는 스팸/보복성 남용 가능성이 후기보다 크기 때문.
  - **관리자 판별 = `SecurityConfig`에 `/admin/**` → `hasRole("ADMIN")` 신규 추가**: 기존 `POST /reviews`(단순 `authenticated()`)와 달리 역할 기반 인가가 이 레포에 처음 등장 — `JwtAuthenticationFilter`가 이미 `"ROLE_" + role.name()` 권한을 심어주고 있어(로그인 구현 당시 선제적으로 깔아둔 설계) 시큐리티 설정 한 줄(`hasRole("ADMIN")`)만으로 403 분기가 즉시 동작했다. `users.role` 컬럼·`Role.ADMIN` enum도 로그인 구현 때부터 이미 존재 — 오케스트레이터 지시대로 role 승격 경로(관리자 지정 방법)는 이번 스코프 밖이며, 테스트는 리플렉션으로 role=ADMIN인 User를 직접 만들어 검증(ReviewServiceTest의 id 리플렉션 주입과 동일 선례).
  - **대상 요약(targetSummary)은 조회 시점에 ReportRepository/ReviewRepository를 조인 없이 개별 조회**: 관리자가 큐 화면에서 원본을 다시 찾아보지 않고 바로 판단할 수 있게(제보 타입/코멘트 또는 별점/코멘트 한 줄 요약). 대상이 이미 삭제됐으면(reports/reviews는 CASCADE 삭제 가능) `targetExists=false`·`targetSummary=null`로 응답해 "유령 신고"를 명시적으로 구분 — 페이지 크기(최대 50)가 작아 N+1이 실질적 성능 문제가 되지 않는 MVP 스코프 판단(대량 조회가 필요해지면 그때 배치 조회로 전환).
  - **resolve는 PENDING에서만 전이(이미 처리된 신고 재처리·PENDING 역행 둘 다 409)**: "선택, 가벼우면" 지시대로 최소 구현 — 상태 전이 규칙을 서비스에 명시해 관리자 UI가 낙관적 동시 처리(같은 신고를 두 관리자가 동시에 여는 경우)를 안전하게 감지할 수 있게 했다.
- 검증: 백엔드 유닛 24건 로컬 green(`FlagServiceTest` 12 — 접수·404×2·409×2(사전체크+레이스)·detail정규화·큐조회+요약·삭제대상처리·resolve+404·409×2, `FlagControllerTest` 7 — 201·401×2·400×2·409, `AdminFlagControllerTest` 5 — 401/403/200×2(pending+resolve 역할게이팅)·size클램프). `FlagFlowIT`(5, 접수→중복409·404·401·관리자큐 역할게이팅·resolve+재처리409)는 ReviewFlowIT와 동일 패턴(실 PostGIS+시큐리티 필터체인)이라 **로컬 colima Docker 미가용으로 skip**(TS-009, SKIP≠통과 — CI green이 최종 게이트). 전체 `./gradlew clean check` 로컬 green(gitleaks 로컬 스캔 무결).
- 산출물: 백엔드 `domain/flag/`(Flag·FlagTargetType·FlagReason·FlagStatus·FlagRepository·FlagService·FlagController·AdminFlagController + dto/{FlagCreateRequest·FlagResponse·FlagResolveRequest·FlagPendingItemResponse·FlagPendingListResponse})·`SecurityConfig`(POST /flags·`/admin/**` 보호 추가)·Flyway `V7__flags.sql`. 테스트 `FlagServiceTest`·`FlagControllerTest`·`AdminFlagControllerTest`·`FlagFlowIT`.
- 다음: trust_score 계산 배선(로그인 제보/후기 가중 — V4 뷰는 이미 준비됨) · 사진 presign(S3) · 신고 대량 발생 시 자동 임계값 알림(심화, 현재 스코프 아님).
- 다음: trust_score 계산(로그인 제보/후기 가중 실배선 — V4 뷰는 이미 준비됨) · 공부공간 데이터 적재 · AI 한줄 요약(Claude).

### 2026-07-09 — 공부 가능 공간 데이터 커버리지 확장(ADR-0006 구현) — 스키마·카테고리·도서관 오픈API·상권정보 스캐폴드
- 한 일: HANDOFF §⑤(ADR-0006)를 구현. `PlaceCategory`에 CAFE·STUDY_CAFE 추가, `places.is_commercial`·`places.deleted_at`(Flyway V5), 도서관은 JSON 오픈API로 전량 페이지네이션 적재(`domain.ingest.openapi`), 상권정보(STUDY_CAFE/CAFE)는 반경 검색 오픈API 스캐폴드(`domain.ingest.storeapi`, 계약 미검증), 소스 공통 soft-delete diff + feature 백필. **프로덕션 실적재는 하지 않음**(사용자 통제, PR만 오픈).
- 결정 & 이유(why):
  - **LIBRARY = CSV가 아니라 JSON 오픈API로 경로 전환(구현 중 실측으로 정정)**: 착수 시엔 ADR 원안대로 CSV 다운로드 경로(`SourceSpec.LIBRARY`)를 만들었으나, `.local/datago.env`의 `DATA_GO_KR_SERVICE_KEY`로 `tn_pubr_public_lbrry_api`를 직접 curl 해보니 지역 파라미터 없이 페이지네이션만으로 **전국 3,555건**을 반환함을 확인(TS-017) — "오픈API=경기도만" 이었던 과거 추정이 틀렸다. CSV 경로를 되돌리고 JSON 오픈API 기반으로 재설계. 레코드마다 `seatCo`(열람좌석수)를 직접 주기 때문에 ADR 원안의 "열람좌석수>0만 백필" 조건을 **정밀하게(레코드 단위)** 구현할 수 있었다(CSV 경로였다면 컬럼 파싱을 얹어야 했을 것을 카테고리 균일 근사로 단순화했을 것).
  - **is_commercial = 컬럼 입력이 아니라 PlaceCategory 파생값**: `PlaceCategory.commercial()`이 upsert 시점에 계산한다(카페류=true). 별도 입력 없이 항상 카테고리와 정합하고, ADR-0006 §1 "카테고리 최소 응집 + 속성 분리" 원칙과도 맞는다(카테고리가 결정하는 값을 별도로 입력받을 이유가 없다).
  - **deleted_at soft-delete는 opt-in(기본 false)**: `IngestionService.ingest(...)`/`PublicLibraryIngestionService.ingestAll(...)`에 `deactivateStale` boolean을 추가하되 기본은 꺼둔다. 전량 스냅샷이 아닌 소스(현재 쉼터 샘플·화장실 실패건 재시도처럼 "부분 파일"을 반복 적재하는 소스)가 실수로 나머지 데이터를 지우는 사고를 막는 안전장치 — CLAUDE.md 원칙3(idempotent, 그러나 파괴적이지 않아야) 정합. `PlaceBulkUpsertRepository.deactivateStale()`도 대상 external_id 집합이 비면 no-op(파싱 실패로 스냅샷이 통째로 비는 사고 방지).
  - **feature 백필은 ON CONFLICT DO NOTHING(UGC 우선)**: 자동 백필(source=PUBLIC, 낮은 confidence)이 이미 유저가 채운 제보/후기 기반 값을 덮어쓰지 않는다 — "UGC가 진실"이라는 그늘의 신뢰도 철학(§5)과 정합. `StudySpaceCoverageIT`에 회귀 테스트로 고정.
  - **상권정보(STUDY_CAFE/CAFE)는 반경 검색(storeListInRadius) 채택, 행정동코드(storeListInDong) 아님**: 후자는 전국 행정동코드 목록(또 다른 데이터셋)이 있어야 순회 가능한데, 우리는 이미 PostGIS 반경검색(`/places?lat=&lng=&radius=`)이 핵심 정신모델이라 같은 패턴(중심좌표+반경)으로 격자 순회하면 별도 코드 목록 없이 전국을 커버할 수 있다. 단, 이 API는 **활용신청 미승인(403 실측)** 이라 계약을 확증 못 했다 — 코드·테스트는 공식 매뉴얼 기반 최선 추정으로 준비하고 클래스 주석·ADR에 "계약 미검증, 승인 후 재검증 필요"를 명시했다(추측을 검증된 것처럼 포장하지 않는다).
  - **업종 분류는 코드가 아니라 상권업종소분류명(indsSclsNm) 텍스트 매칭(StoreCategoryMapper)**: 2023년 개편된 업종코드 매핑표(15067631)를 아직 실측하지 못해, 코드값을 하드코딩하는 대신 "독서실/스터디카페/스터디룸"·"커피/카페/다방" 키워드 매칭을 1차 판별로 쓴다. 코드가 확정되면 이 클래스만 교체하면 되고 호출부(StoreIngestionService)는 영향받지 않는다 — 불확실성을 한 곳에 격리.
  - **상권정보에는 soft-delete diff를 의도적으로 붙이지 않음**: 반경 검색 1회는 전국 스냅샷의 부분집합이라, 여기서 `deactivateStale`을 걸면 "이번 호출에 안 보인 다른 지역 장소"까지 지우는 사고가 난다. 전국 커버리지가 여러 반경 호출로 완성되는 P3 무인화 단계에서 스냅샷을 합쳐 diff하는 것으로 미룬다(로드맵과 정합, 범위 임의 확장 안 함).
  - **fallbackId 유틸 추출(`IngestIds`)**: CSV 파서(`StandardCsvParser.fallbackId`)와 JSON 오픈API 경로(도서관·상권정보) 양쪽이 "고유번호 없으면 sha256(name|address)" 규칙을 공유해야 해서 패키지 공용 유틸로 승격. 기존 CSV 파서 테스트(`StandardCsvParser.fallbackId` 직접 호출)는 위임으로 하위호환 유지.
  - **JaCoCo floor 0.35→0.60 상향**: 신규 순수 단위테스트(오픈API 클라이언트·서비스, Mockito 기반, DB 불필요)가 대거 추가되며 로컬 LINE 커버리지가 65.7%로 뛰어 프로젝트 관례("floor는 측정치 바로 아래")대로 재측정 반영.
  - **트렌드 근거(2026-07 확인)**: data.go.kr 오픈API는 계정당 인증키 하나가 모든 활용신청에 공유되지만, 데이터셋별 "활용신청" 승인은 별개(도서관=즉시, 상가정보=대기)라는 걸 실측으로 확인 — 향후 세션이 새 data.go.kr 소스를 붙일 때 승인 여부부터 확인하도록 남긴다.
- 검증: 신규 단위테스트 25건(파서·클라이언트 MockRestServiceServer·서비스 Mockito) 로컬 green + 기존 전체 회귀 0(133 total, 0 failures/errors, 29 skipped=Docker 미가용 IT). `StudySpaceCoverageIT`(실 PostGIS, 4건: is_commercial/조건부 백필/UGC 비침습/soft-delete diff+안전장치)는 로컬 colima 이슈로 skip(TS-009 선례) → **CI green으로 최종 판정**. `./gradlew check`(jacocoTestCoverageVerification 포함) 로컬 green.
- 산출물: 스키마 `V5__place_commercial_softdelete.sql`. 백엔드 `domain/place/{PlaceCategory,Place,PlaceRepository}`(is_commercial·deleted_at·commercial()·쿼리 필터) · `domain/ingest/{IngestIds,FeatureSpec,DefaultFeatureBackfill,IngestionService,PlaceBulkUpsertRepository,IngestionRunner,SourceSpec,StandardCsvParser}`(soft-delete diff·feature 백필·CLI 라우팅) · `domain/ingest/openapi/*`(도서관 오픈API, 실측 검증) · `domain/ingest/storeapi/*`(상권정보 오픈API, 계약 미검증) · `application.yml`(datago.service-key) · 테스트 25건 + IT 4건.
- 다음(사용자 실행): ① 도서관 프로덕션 실적재(`--ingest.source=library`, 안전 검토 후 `deactivate-stale=true`로 최초 풀스냅샷). ② 상권정보(`study_cafe`/`cafe`) "상가업소정보" 오픈API 활용신청 승인 대기 → 승인되면 `SmallBusinessStoreApiClient` 실 호출로 계약 재검증(필드명·에러 포맷) 후 격자 좌표 순회로 실적재. ③ 업종코드 매핑표(15067631) 실측해 `StoreCategoryMapper`를 코드 기반으로 교체(선택, 텍스트 매칭도 당장은 동작).
- 관련: 브랜치 `feat/study-space-coverage-p3`, ADR-0006(Proposed→Accepted, "구현 정정" 섹션), TS-017, `docs/adr/0006-study-space-coverage-expansion.md`.
### 2026-07-09 — trust_score 계산 + 제보 가중 실배선 (P2, 브랜치 `feat/trust-score-p2`)
- 한 일: CLAUDE.md §5 "제보는 trust_score로 가중"의 남은 조각을 완성. **정확히 확인해 보니 SQL 쪽(가중 공식 자체)은 ADR-0007/V4에서 이미 완성돼 있었다** — `place_report_signals` 뷰가 처음부터 `users` LEFT JOIN + `0.7 + 0.3·min(trust_score/100,1)`로 comfort/risk를 가중한다. **진짜 빠진 것은 두 가지**: ① `users.trust_score`가 한 번도 계산되지 않아 모든 로그인 유저가 신규 유저와 동일하게 0(=익명과 동일 0.7 가중치)이었다는 점, ② `POST /reports`가 애초에 로그인 여부를 전혀 안 봐서(컨트롤러에 `@AuthenticationPrincipal`조차 없음) 로그인 유저도 항상 `user_id=NULL`로 저장되고 있었다는 점(`Report.anonymous()` 팩토리에 userId 파라미터 자체가 없었다 — 코드 주석에 "P2 인증 붙으면 채워진다"로 이미 예고돼 있던 미완 조각). 즉 신뢰도 가중 파이프라인은 배관은 있었지만 물이 한 방울도 안 흐르고 있었다.
- 결정 & 이유(why):
  - **trust_score 공식 = `100 · volumeScore^0.7 · ageScore^0.3`(가중기하평균, "곱" 결합)** — `volumeScore = clamp01(ln(1+contributions)/ln(51))`(로그 스케일 diminishing returns, contributions = reportCount + 2·reviewCount — 후기는 로그인 필수·영구 콘텐츠라 제보보다 고신뢰 신호), `ageScore = clamp01(accountAgeDays/30)`. 웹 검색(2026-07)으로 확인: 위키피디아 autoconfirmed는 **계정연령(4일) AND 편집수(10회)를 동시에 요구**하는 이중 게이트다(나이만으로도, 활동만으로도 승격 불가) — 이 프로젝트의 스팸 억제 목적과 정확히 같은 문제다. 처음엔 가중합(`0.7·volume+0.3·age`)을 검토했으나, 레이트리밋(분당3·시간당10) 안에서도 새 계정이 몇 시간 안에 volumeScore를 포화시켜 age=0인데도 70점대가 나오는 걸 발견 — "스팸 억제"라는 목적을 정면으로 비껴갔다. **곱(지수합=1인 가중기하평균)으로 바꾸면 두 조건을 동시에 요구**해 한쪽이 0에 가까우면 전체도 0에 가깝게 끌려간다(위키피디아 AND 게이트를 연속값으로 일반화). `TrustScore.java` 클래스 주석에 수치 예시와 함께 근거를 남겼다.
  - **trust_score는 0~100 스케일 유지(0~1 아님)** — 오케스트레이터 지시 초안은 "0~1 정규화"를 언급했으나, `users.trust_score`(V2 DDL `DOUBLE PRECISION`)와 V4 뷰의 `COALESCE(u.trust_score,0)/100.0` 나눗셈이 **이미 0~100 스케일을 전제로 라이브 중**이다. 0~1로 저장하면 trust=1.0(만점)도 `1/100=0.01`로 나눠져 사실상 익명과 구분이 안 되는 회귀 버그가 된다. 내부 서브 성분(volumeScore·ageScore)은 0~1로 정규화하되 최종 출력만 기존 계약(0~100)에 맞췄다 — "0~1 정규화"라는 지시의 취지(정규화된 성분 조합)는 살리고, 이미 라이브인 저장 계약은 깨지 않는 절충.
  - **V6 마이그레이션은 뷰를 재작성하지 않고, 인덱스 + 코멘트만 추가** — 오케스트레이터는 "뷰를 재생성하는 V6"를 명시했지만, 위에서 확인했듯 뷰의 가중 SQL 자체(`tf.f` 서브쿼리)는 이미 정답이라 다시 쓸 이유가 없다(뷰 시맨틱을 깨지 말라는 지시와도 정합 — 손대지 않는 게 가장 안전하게 지키는 방법). 대신 V6(`V6__place_report_signals_trust_weight.sql`, 예약 번호는 그대로 사용)에는 **진짜 필요했던 것**: `TrustScoreService`의 `countByUserId` 쿼리가 전체스캔하지 않도록 `idx_reports_user`(부분 인덱스, `user_id IS NOT NULL` — 아직 익명이 다수라 크기 절약)와 `idx_reviews_user`(전체 인덱스, user_id NOT NULL 컬럼)를 추가하고, 뷰에 `COMMENT ON VIEW`로 "trust_score 산출은 P2 TrustScoreService가 담당한다"는 감사 기록만 남겼다(CREATE OR REPLACE로 동일 정의를 재선언하는 no-op은 하지 않음 — 아무 효과 없이 리스크만 지는 행위). **명시적 이탈이라 최종 보고에서 오케스트레이터에게 다시 플래그함.**
  - **재계산 시점 = 온디맨드(배치 아님)** — 유저가 제보/후기를 저장하는 그 트랜잭션 안에서 `TrustScoreService.recalculate(userId)`를 호출(카운트 쿼리 2회 + 저장 1회). 배치(EventBridge 등 전체 유저 주기 재계산)를 검토했으나 ① UGC가 레이트리밋으로 저빈도라 배치가 얻을 성능 이득이 없고 ② 이 프로젝트의 스케줄러 인프라는 P3 공공데이터 동기화용으로만 계획돼 있어 trust_score 하나 때문에 새 인프라를 얹는 건 과설계(CLAUDE.md §0.2)다. 온디맨드는 뷰가 조회 시점에 최신값을 읽으므로 "방금 로그인해 첫 제보를 남긴 유저"도 다음 제보부터 즉시 신뢰도가 반영돼 배치 지연이 없다는 제품 경험 이점도 있다.
  - **로그인 제보의 userId는 "익명으로 표시" 선택과 무관하게 항상 기록** — CLAUDE.md §6 MVP 화면표는 "제보하기: … 익명 여부(로그인 시 신뢰도 반영)"라고 명시한다. 즉 로그인 유저가 UI에서 "익명으로 제보"를 선택해도(닉네임을 안 보여줘도) 신뢰도 가중은 그대로 반영돼야 한다는 뜻으로 읽었다 — `is_anonymous`는 순수 표시 여부, `user_id`는 별개로 항상 기록. 비로그인(토큰 없음)만 진짜 `user_id=NULL`.
  - **`POST /reports`는 여전히 permitAll, 인증은 선택** — SecurityConfig를 안 건드렸다(레이트리밋 XFF 신뢰경계 등 기존 계약 무회귀). `JwtAuthenticationFilter`가 모든 요청에서 Bearer 토큰이 있으면 SecurityContext를 채우는(permitAll 여부와 무관) 기존 동작을 그대로 활용해 `@AuthenticationPrincipal`을 nullable로 받았다 — 토큰이 없거나 무효면 null(기존 완전 익명 동작과 100% 동일, 회귀 없음), 있으면 principal이 채워진다.
- 검증: `TrustScoreTest`(7, 신규유저=0·활동+연령 충분=high·연령0이면 활동무관 억제="곱 결합" 핵심 증명·연령만 있고 활동0이면 0·후기 2배 가중 등가성·범위 방어·단조성) + `TrustScoreServiceTest`(3, 카운트→공식→저장·유저없음 방어·신규유저 0) + `ReportServiceTest`(4, 비로그인 무-userId·로그인 userId기록+재계산 트리거·"익명표시"여도 신뢰도 유지·404시 재계산 안 함, 신규) + `ReportControllerTest`(+3: 비로그인 principal null 확인, Authorization 헤더 있으면 principal 전달, 무효토큰이면 permitAll이라 401 아니라 익명 폴백 — ReviewControllerTest의 `@Import(SecurityConfig+ServletWebSecurityAutoConfiguration+SecurityFilterAutoConfiguration)` 패턴, TS-015 재사용) + `ReviewServiceTest`(+2: 후기 작성 시 재계산 호출·404시 미호출). `./gradlew clean check` 로컬 **175건 green, 실패/에러 0**(skip 34건=Testcontainers IT, colima 이슈로 로컬 skip — TS-009 패턴, CI가 최종 게이트). 신규/변경 스위트만 별도 확인: TrustScoreTest 7/7·TrustScoreServiceTest 3/3·ReportServiceTest 4/4·ReportControllerTest 9/9·ReviewServiceTest 7/7 전부 green.
- 산출물: 백엔드 `domain/auth/TrustScore.java`(신규, 순수 함수)·`domain/auth/TrustScoreService.java`(신규, 온디맨드 재계산)·`domain/auth/User.java`(`updateTrustScore` 추가)·`domain/report/Report.java`(`of(userId,...)` 팩토리 추가, `anonymous()`는 위임 유지)·`domain/report/ReportController.java`(`@AuthenticationPrincipal` 선택적 추가)·`domain/report/ReportService.java`(principal 파라미터, trust 재계산 호출)·`domain/report/ReportRepository.java`(`countByUserId`)·`domain/review/ReviewRepository.java`(`countByUserId`)·`domain/review/ReviewService.java`(trust 재계산 호출). 테스트 `TrustScoreTest`·`TrustScoreServiceTest`·`ReportServiceTest`(신규 3파일) + `ReportControllerTest`·`ReviewServiceTest`(기존 갱신). 마이그레이션 `V6__place_report_signals_trust_weight.sql`(인덱스 2개 + 뷰 코멘트, 뷰 SELECT 로직은 무변경).
- 다음: 공부공간 데이터 적재(§⑤) · AI 한줄 요약(Claude) · GPS 방문 인증(verified, §④ 백로그) — verified가 붙으면 trust_score의 volumeScore에 "검증된 활동만" 가중치를 더 얹는 확장점으로 설계해 뒀다(공식이 이미 활동량 기반이라 additive 확장 가능).
### 2026-07-09 — S3 사진 업로드 presign (P2): 제보/후기 사진 슬롯 풀스택
- 한 일: HANDOFF·CLAUDE.md §7·§9가 지목한 **사진 업로드**를 풀스택으로 완성. 백엔드 `POST /photos/presign`(S3Presigner로 presigned PUT URL 발급, 파일은 서버를 거치지 않음) + 인프라(S3 버킷·CORS·태스크롤 IAM) + 프론트(제보/후기 폼에 실 카메라 슬롯, presign→S3 직접 PUT→objectUrl을 제출 바디에 첨부). `reports.photo_url`·`reviews.photos_json`은 V2에 이미 있는 컬럼이라 **신규 Flyway 불필요** — `Report` 엔티티에 photoUrl 매핑만 추가했고, `Review`/`ReviewCreateRequest`는 review 브랜치(PR #31)가 이미 `photos: string[]` 슬롯을 준비해둬서 백엔드 변경이 필요 없었다(presign 결과를 그 배열에 얹기만 하면 됨).
- 결정 & 이유(why):
  - **AWS SDK v2 `software.amazon.awssdk:s3` 직접 사용, spring-cloud-aws 배제**: 필요한 건 `S3Presigner` 하나뿐이라(파일을 서버가 다루지 않음) spring-cloud-aws의 자동구성 전체(S3Client·리소스로더 등)는 과설계. BOM `software.amazon.awssdk:bom:2.46.6`(2026-07 시점 최신 안정 라인, 웹 확인)으로 버전만 고정.
  - **정적 액세스키 없이 IAM 롤로만**: `S3Presigner`는 자격증명을 지정하지 않고 SDK 기본 체인에 맡긴다 — ECS에서는 태스크 롤(`iam.tf` 신규 `ecs_task_s3_photos`, 버킷 ARN 한정 PutObject/GetObject만), 로컬은 `~/.aws`. 규칙 D(비밀은 SSM/env로만, 코드에 안 둠)와 정합 — 애초에 발급할 "정적 키"가 없다.
  - **크기 상한은 presigned POST의 content-length-range가 아니라 "서명된 Content-Length"로 강제**: 웹 검색(2026-07)으로 `s3:RequestObjectSize` IAM 조건키의 존재를 AWS 공식 서비스오소라이제이션 레퍼런스(`list_amazons3.html`)에서 재확인하려 했으나 **PutObject 액션에 그 조건키가 없었다**(확인 불가한 기능에 기대지 않는다, §0-B 원칙) — 대신 SigV4의 표준 동작(서명에 포함한 헤더는 실제 요청이 정확히 일치해야 S3가 받아준다)을 그대로 썼다: `PutObjectRequest`에 `contentType`·`contentLength`를 실어 presign하면, 브라우저가 다른 타입/크기로 PUT할 경우 S3가 서명 불일치로 거부한다. presigned POST(별도 폼 필드·정책 문서 필요)보다 단순해 PUT 한 번으로 끝나는 기존 설계(§9 API 초안이 이미 "PUT URL"로 명시)와도 맞는다.
  - **화이트리스트 3종(jpeg/png/webp) + 8MB 상한**: 휴대폰 카메라·일반 편집기가 흔히 만드는 타입만 허용(HEIC 등은 스코프 밖 — 브라우저가 흔히 변환해줌). 8MB는 휴대폰 사진 1장 기준 여유치이자 presign 요청 자체(레이트리밋 5/min·20/hour)와 곱해도 시간당 최대 ~160MB로 방어 가능한 수준.
  - **로그인 요구는 용도별로 분리(report=익명 허용, review=로그인 필수)**: §1 UGC 2단 구조를 그대로 따른다 — 제보 자체가 "한 탭 낮은 부담" 설계라 사진 슬롯만 로그인을 요구하면 모순된다. 대신 익명 presign 남용은 **`PhotoPresignRateLimiter`**(ReportRateLimiter와 동일 설계 — 원자적 compute·맵 상한 evict, TS-008 하드닝 계승, 5/min·20/hour)로 방어. review는 POST /reviews와 동일 정책(로그인 필수)을 유지 — `PhotoController`는 permitAll이지만 `PhotoService`가 `purpose=REVIEW && !authenticated`를 401로 명시 거부(컨트롤러 레벨에서 막으면 report 익명 경로까지 막혀버리므로 서비스 레벨에서 분기).
  - **레이트리미터를 `ReportRateLimiter`와 별개로 새로 작성(추상화 안 함)**: 소비자가 report/photo 둘뿐이라 "rule of three"에 따라 지금은 중복을 감수했다(클래스 주석에 근거 명시). 세 번째 소비자가 생기면 그때 `global.web`으로 추출.
  - **버킷은 비공개 유지, MVP는 오브젝트 URL을 그대로 저장**: `aws_s3_bucket_public_access_block` 전면 차단 + `BucketOwnerEnforced`(ACL 비활성, 2020년대 AWS 기본 권장). §7이 명시한 대로 presigned GET/CloudFront는 스코프 밖 — 지금 저장하는 `objectUrl`은 실제 뷰잉을 열기 전까진 403이 나는 게 정상(다음 조각으로 HANDOFF에 남김).
  - **CORS 오리진 = 프로덕션 + 로컬만**: `https://geuneul.vercel.app`, `http://localhost:3000`(frontend package.json dev 기본 포트). 와일드카드 금지.
- 검증: 백엔드 신규 유닛 21건 로컬 green — `PhotoServiceTest`(9, 정상 발급·기본 purpose·review 인증/미인증·화이트리스트 밖 400·8MB 초과 400·잘못된 purpose 400·버킷 미설정 IllegalStateException·키 유일성 — **실 AWS 자격증명·네트워크 없이 정적 자격증명으로 presign 로컬 서명만 검증**), `PhotoControllerTest`(7, 200·400×2·429·미인증 authenticated=false 전파·Bearer 인증 authenticated=true 전파·서비스발 401 전파, `ReviewControllerTest`가 확립한 `ServletWebSecurityAutoConfiguration`+`SecurityFilterAutoConfiguration` Import 패턴 재사용), `PhotoPresignRateLimiterTest`(5, ReportRateLimiterTest와 동형). `ReportControllerTest`·`ReportFlowIT`에 photoUrl 왕복·검증 테스트 추가(https 아니면 400, 없으면 선택 필드로 통과). 전체 `./gradlew clean check` 로컬 green(신규 실패 0, IT는 colima로 skip — TS-009). `terraform validate` green(`aws_s3_bucket_lifecycle_configuration`에 `filter {}` 명시 필요 — 최신 프로바이더가 filter/prefix 없는 규칙을 경고). 프론트 `typecheck`·`lint`·`build` 전부 green(`/api/photos/presign` 라우트 등록 확인).
- 산출물: 백엔드 `domain/photo/`(PhotoPurpose·PhotoService·PhotoPresignRateLimiter·PhotoController + dto/{PhotoPresignRequest·PhotoPresignResponse})·`global/config/S3Config`(S3Presigner 빈)·`Report.java`(photoUrl 매핑, 5-인자 팩토리 오버로드로 기존 4-인자 호출부 무회귀)·`ReportCreateRequest`/`ReportResponse`/`ReportService`(photoUrl 왕복)·`build.gradle`(AWS SDK BOM+s3)·`application.yml`(aws.s3.bucket/region). 인프라 `infra/terraform/s3.tf`(신규 — 버킷·퍼블릭차단·CORS·라이프사이클)·`iam.tf`(ecs_task_s3_photos 정책)·`ecs.tf`(S3_BUCKET_NAME·AWS_REGION env, 문서화용)·`outputs.tf`(s3_photos_bucket). 프론트 `types/photo.ts`(신규)·`types/place.ts`(Report/ReportCreatePayload photoUrl)·`lib/api.ts`(presignPhoto·uploadPhotoToS3)·`lib/backend.ts`(proxyPhotoPresign — report 익명 XFF 보존 + review는 쿠키 있으면 Bearer 첨부, 없어도 차단하지 않음)·`lib/hooks.ts`(usePhotoUpload 공용 훅)·`app/api/photos/presign/route.ts`(신규)·`app/(shell)/report/page.tsx`(실 카메라 슬롯으로 교체)·`components/place/ReviewsSection.tsx`(후기 폼에 사진 슬롯 추가)·`components/place/PlaceDetailOverlay.tsx`(최근 제보 사진 썸네일).
- 다음(배포 절차, orchestrator가 실행): ① `terraform apply`(S3 버킷+CORS+라이프사이클, IAM 정책 — plan 요약은 PR 보고 참고, 비용 거의 0). ② 라이브 태스크데프에 `S3_BUCKET_NAME`(=terraform output `s3_photos_bucket`)·`AWS_REGION=ap-northeast-2` env 추가한 새 rev 등록 + `update-service --force-new-deployment`(proxy-secret rev13 패턴). ③ 실측: 제보 화면에서 사진 촬영→업로드→최근 제보 썸네일 확인, 후기 폼에서도 동일. ④ 이후: presigned GET 또는 CloudFront로 실제 뷰잉 열기(지금은 objectUrl 저장만, 비공개 버킷이라 직접 접근은 403) — HANDOFF 다음 조각.
- 관련: 브랜치 `feat/photo-presign-p2`, `domain/photo/*`, `s3.tf`, CLAUDE.md §7(Storage)·§9(POST /photos/presign), TS-008(레이트리미터 설계 계승).
### 2026-07-10 — AI 한줄 요약 (P3, 곁다리, 브랜치 `feat/ai-summary-p3`, ADR-0010)
- 한 일: CLAUDE.md §3/§6이 지정한 마지막 P3 조각 — 장소 상세(`GET /places/{id}`)에 "최근 제보 기준" 한국어 한 문장 AI 요약을 additive로 추가. `domain/ai`(신규 패키지)에 `OpenRouterClient`(OpenAI 호환 Chat Completions, WeatherClient 회복탄력 패턴 그대로)와 `AiSummaryService`(유효 제보 조회→프롬프트 조립→호출→Redis 캐시)를 만들고, `PlaceSearchService.getById`에서만 호출해 목록/반경/bounds 경로는 건드리지 않았다.
- 결정 & 이유(why): **프로바이더 = OpenRouter(Anthropic 아님)** — 이 환경에 Anthropic API 키가 없어(`claude-api` 스킬의 인증 해석 순서를 전부 확인했으나 어느 것도 사용 가능하지 않음) CLAUDE.md §8 "Claude API 기본"에서 명시적으로 이탈했다(§0-B 프로토콜에 따라 여기·ADR-0010에 기록). 사용자가 대화 중 명시 허용한 멀티프로바이더 무료/저가 폴백 키체인(`.local/ai.env`) 중 `SSUAI_OPENROUTER_API_KEY`를 프라이머리로 썼다. 상세 근거·검토한 대안(오토라우터·유료 기본모델·제보없음 정적문구 캐시 등)은 전부 **ADR-0010**에 정리 — 이 항목은 요약만 남긴다.
  - **모델은 설정값(`ai.openrouter.model`), 하드코딩 아님** — 기본값 `qwen/qwen3-next-80b-a3b-instruct:free`. 2026-07 웹검색으로 Qwen3 계열이 119개 언어를 지원하며 한중일 성능이 특히 우수하다는 근거를 확인했고(ADR-0010 References), OpenRouter 무료 티어(20 req/min)로 이 프로젝트 트래픽(요청당 1회, 캐시로 재사용)엔 충분하다. 무료 모델 라인업이 주기적으로 회전하는 리스크는 "설정만 바꾸면 교체 가능"으로 흡수했다.
  - **캐시 = Redis "aiSummary"(장소별, TTL 3h)** — WeatherClient/RedisCacheConfig의 기존 패턴(TS-011 타입 바인드 직렬화·TS-012 캐시히트 500 재발 방지)을 그대로 재사용해 `JacksonJsonRedisSerializer<String>` + `disableCachingNullValues`로 구성했다. TTL은 지시 범위(1~6h)의 중간값 — 너무 짧으면 비용 방어 효과가 작고, 너무 길면 새 제보가 와도 요약이 안 바뀐다.
  - **유효 제보 0건이면 AI를 호출하지 않는다(정적 문구 대신 null)** — 지시사항이 허용한 두 대안 중 "생성 안 함"을 택했다: 상세 화면이 이미 "최근 제보 없음"을 별도로 보여주고, 정적 문구를 캐시했다면 그 TTL 동안 새 제보가 들어와도 갱신이 안 됐을 것이다. `AiSummaryService.summarize`가 리포지토리 조회만 하고 empty를 반환하면(캐시 미적용, unless SpEL) 다음 호출에서 곧바로 재평가된다.
  - **graceful degradation은 새로 만들 필요 없이 WeatherClient 패턴을 복제** — `OpenRouterClient.complete`가 키 미설정·네트워크 오류·타임아웃(연결 1.5s·읽기 2.5s)·5xx·빈 응답·JSON 파싱 실패를 전부 여기서 삼켜 `Optional.empty()`로 반환한다. `PlaceSearchService.getById`는 `.orElse(null)`로 단순 위임 — AI가 죽어도 상세 API는 항상 200이다.
  - **`PlaceResponse`에 4-인자 오버로드(aiSummary 인자)만 신설, 기존 3-인자는 위임 유지** — ADR-0009가 세운 "레거시 오버로드 위임" 패턴 재사용. `RecommendationService`·`PlaceSearchService.searchRadius/searchBounds`는 기존 3-인자 호출부를 전혀 안 건드려 무회귀.
  - **입력 프롬프트는 새 쿼리를 만들지 않고 기존 `findTop20ByPlaceIdAndExpiresAtAfterOrderByCreatedAtDesc` 재사용** — "최근 제보" 섹션과 같은 소스. 타입별 최신 1건만 남기고(중복 제거) 최신순 상한 8종만 담아 토큰·비용을 방어한다.
  - **시스템 프롬프트에 공포 조장 금지 규칙을 명시 텍스트로 강제**(CLAUDE.md §0-6) — "위험!" 대신 "최근 침수 제보 있음, 우회 권장" 식 표현을 지시하고, 단위테스트로 프롬프트 문자열을 못 박았다.
- 검증: 백엔드 신규 유닛 24건 로컬 green — `OpenRouterClientTest`(8, MockRestServiceServer로 요청 바디/헤더(Authorization Bearer·model·messages 역할·max_tokens)·응답 파싱 계약 검증(TS-004 교훈) + 빈 choices·blank content·5xx·429·malformed JSON·키 없음·키 blank 전부 empty), `AiSummaryServiceTest`(6, 제보없음→AI미호출·제보있음→위임·클라이언트실패→empty전파·공포조장금지 프롬프트 검증·동일타입 중복제거(최신값 유지)·8종 상한), `AiSummaryCacheProxyTest`(2, WeatherCacheProxyTest와 동형 — present 결과 캐시+2회차 리포지토리/클라이언트 재호출 없음, empty 결과는 캐시 안 됨). `PlaceSearchServiceTest`에 6건 추가(getById 1회 호출+응답반영·AI실패시 null폴백·searchRadius/searchBounds/searchNearest 3경로 모두 AI 미호출 — "상세 전용" 계약을 명시적으로 못 박음). `./gradlew clean check` 로컬 **271건 total, 실패/에러 0**(skip 43=Testcontainers IT, colima 이슈로 로컬 skip — TS-009 패턴, CI가 최종 게이트). `terraform validate`·`terraform fmt -check` green(ssm.tf·variables.tf·ecs.tf 신규 리소스/변수).
- 산출물: 백엔드 `domain/ai/`(OpenRouterClient·AiSummaryService, 신규)·`domain/place/dto/PlaceResponse`(aiSummary 필드 + 4-인자 오버로드)·`domain/place/PlaceSearchService`(AiSummaryService 주입, getById에서만 호출)·`global/config/RedisCacheConfig`(aiSummary 캐시 신설)·`application.yml`(ai.openrouter.api-key/base-url/model). 인프라 `infra/terraform/ssm.tf`(openrouter_api_key SecureString)·`variables.tf`(openrouter_api_key 변수)·`ecs.tf`(OPENROUTER_API_KEY secret 항목) — **apply 안 함, 스캐폴드만**. 테스트 `OpenRouterClientTest`·`AiSummaryServiceTest`·`AiSummaryCacheProxyTest`(신규 3파일) + `PlaceSearchServiceTest`(갱신). 문서 `docs/adr/0010-ai-summary-openrouter-provider.md`(신규).
- 다음(배포 절차, orchestrator가 실행): ① SSM 파라미터 `/geuneul/openrouter_api_key`(SecureString) 생성 — `terraform apply`(tfvars에 `openrouter_api_key` 값 추가 필요, `.local/ai.env`의 `SSUAI_OPENROUTER_API_KEY` 값 사용). ② 라이브 태스크데프에 `OPENROUTER_API_KEY` secret 항목이 반영된 새 rev 등록(`describe`→env/secret 추가→`register`→`update-service --force-new-deployment`, proxy-secret rev13 패턴) — task role/execution role IAM은 `/geuneul/*` 와일드카드라 무변경. ③ 실측: 제보가 있는 장소 상세에서 `aiSummary` 필드 확인, 제보 없는 장소는 null 확인, 캐시 히트(같은 장소 재조회 시 지연 감소) 확인.
- 관련: 브랜치 `feat/ai-summary-p3`, `domain/ai/*`, ADR-0010, CLAUDE.md §0-B(의사결정 프로토콜)·§0-8/9(AI는 곁다리)·§3/§6(AI 요약 MVP)·§8(AI 스택), ADR-0009(같은 "요청당 1회·graceful degradation·레거시 오버로드 위임" 패턴 재사용), TS-011/TS-012(캐시 직렬화 하드닝 계승).

### 2026-07-10 — 공공데이터 주기 동기화 무인화 (P3, EventBridge Scheduler → ECS RunTask, 브랜치 `feat/scheduled-sync-p3`)
- 한 일: 로드맵 P3의 마지막 조각 "공공데이터 주기 동기화 스케줄(멱등 upsert 재실행 + 스냅샷 소실 행 soft-delete + 오픈API serviceKey 다운로드 무인화)"을 마무리했다. 멱등 upsert·soft-delete diff(`deactivateStale`)는 ADR-0002/0006에서 이미 완성돼 있어, 이번 범위는 그 파이프라인을 **사람 없이** 정기 실행하는 오케스트레이션 3가지: ① EventBridge Scheduler(월1회)→ECS RunTask 배선, ② `library` 소스가 무인 환경에서도 다운로드까지 자족하도록 `DATA_GO_KR_SERVICE_KEY`를 상시 SSM/ECS secret으로 승격, ③ 스케줄 중복·수동 실행 동시성으로부터 인제스천을 보호하는 앱 레벨 가드.
- 결정 & 이유(why) — 상세 근거는 [ADR-0011](./docs/adr/0011-scheduled-public-data-sync.md)에 전부 기록, 요지만:
  - **스케줄러→ECS 연결에 Terraform `aws_scheduler_schedule`의 네이티브 `ecs_parameters` 블록 대신 Universal Target(`arn:aws:scheduler:::aws-sdk:ecs:runTask`)을 썼다.** 웹 검색(2026-07)으로 `ecs_parameters`가 container overrides를 지원하지 않는다는 걸 확인했다(hashicorp/terraform-provider-aws#34057, 2023년 제기 후 여전히 오픈) — `--ingest.source=library` 같은 커맨드 오버라이드를 실을 방법이 없어, AWS 공식 "Universal Target"(2023 출시, SDK 액션을 ARN으로 직접 호출)으로 전환했다. `input` JSON은 ECS RunTask API 그대로(camelCase)라 `prod-ingest.sh`의 `aws ecs run-task --overrides`와 같은 셰이프 — 두 실행 경로(수동·무인)가 같은 정신모델을 공유한다.
  - **스케줄 대상은 `library` 하나만.** CSV 소스(쉼터/화장실)는 스냅샷이 GitHub Release 고정 자산이라 "주기 재동기화"의 의미가 약함(멱등이라 안전하지만 재실행이 무의미). `library`(전국도서관표준데이터, ADR-0006)는 오픈API 원본 자체가 갱신되고 파일/URL 없이 페이지네이션으로 전량 자체 수집 — P3 문구 "다운로드까지 무인화"가 정확히 이 소스를 가리킨다.
  - **다운로드 무인화의 실체 = `DATA_GO_KR_SERVICE_KEY`를 다른 시크릿(kma/kakao 등)과 동일 패턴으로 SSM SecureString + ECS task def secrets에 상시 배선.** 인제스천 로직 자체는 ADR-0006에서 이미 완성돼 있었고, 진짜 빠진 건 "무인 환경에도 키가 있는가" 하나였다(지금까지는 `prod-ingest.sh`처럼 사람이 매번 셸 환경변수로 넘겨야 했음).
  - **동시 실행 방지는 새 인프라(SQS 락, DynamoDB 조건부쓰기) 대신 Postgres 세션 수준 advisory lock(`IngestBatchLock`, `pg_try_advisory_lock`).** 월1회 저빈도 스케줄에 새 인프라를 얹는 건 CLAUDE.md §0.2 과설계 금지 위반 — 이미 있는 RDS로 논블로킹 상호배제를 얻는다. 락을 못 얻으면 "실패"가 아니라 "건너뜀"으로 처리해 exitCode=0(알림 노이즈 방지, 다음 스케줄이 사실상 재시도). `IngestionRunner.run()`을 dispatch 로직 분리 + `batchLock.runExclusive(...)` 감싸기로 리팩터링해서, **library뿐 아니라 모든 `--ingest.source=` 실행(CSV 포함)**이 이 가드를 통과하게 했다 — 나중에 스케줄 대상을 늘려도 재작업이 필요 없다.
  - **HikariCP + advisory lock 함정을 구현 전에 미리 잡았다(TS-019).** `JdbcTemplate`처럼 매번 커넥션을 열고-닫으면, 풀이 물리 커넥션(=Postgres 세션)을 재사용하는 한 lock/unlock이 다른 물리 커넥션으로 갈 수 있어 락이 샌다. `IngestBatchLock`은 `DataSource.getConnection()`으로 얻은 **단 하나의 Connection을 lock부터 unlock까지 계속 물고 있다가** close한다.
  - **`terraform apply`·스케줄 활성화는 이번 스코프에서 실행하지 않는다(오케스트레이터 지시).** `var.ingest_schedule_enabled`(기본 false)로 `aws_scheduler_schedule.state`를 DISABLED로 고정 — 코드 레벨에서 "검토 없이 적용해도 자동 실행 안 됨"을 강제했다.
- 검증: 신규 `IngestBatchLockTest`(4건, Mockito — 락 획득 시 action 실행+unlock, 미획득 시 action 미실행+unlock 미호출, action 예외 시에도 unlock은 수행+예외 전파, SQLException은 IllegalStateException으로 래핑) 전부 로컬 green. 신규 `IngestBatchLockIT`(2건, 실 PostGIS Testcontainers — ① 두 스레드가 동시에 락을 다투면 하나만 이기고 하나는 논블로킹으로 즉시 false, ② 락 해제 후 재시도는 다시 성공/누수 없음)는 로컬 colima 이슈로 skip(TS-009 패턴), CI에서 최종 검증. `IngestionRunner`는 `dispatch()` 메서드로 분기 로직을 분리하고 `run()`을 `IngestBatchLock.runExclusive(Callable<Void>)`로 감쌌다(체크 예외 IOException을 그대로 흘려보내기 위해 `run()` throws 절을 `IOException`→`Exception`으로 넓히고, catch도 RuntimeException/체크예외 구분 없이 하나로 합쳤다 — 기존 exit-after 코드 경로 동작은 그대로 보존). `./gradlew clean check` 로컬 **256건 실행, 실패/에러 0**(skip 45건=Testcontainers IT, CI가 최종 게이트). `terraform validate`·`terraform fmt -check` 모두 green(provider v5.100.0으로 init, apply는 미실행).
- 산출물: 백엔드 `domain/ingest/IngestBatchLock.java`(신규, advisory lock 래퍼)·`domain/ingest/IngestionRunner.java`(dispatch 분리 + 락 배선). 테스트 `domain/ingest/IngestBatchLockTest.java`(신규, Mockito 단위)·`domain/ingest/IngestBatchLockIT.java`(신규, 실 PostGIS 동시성). 인프라 `infra/terraform/scheduler.tf`(신규 — EventBridge Scheduler 실행 롤+정책+스케줄 본체, Universal Target)·`variables.tf`(`datago_service_key`·`ingest_schedule_enabled`)·`ssm.tf`(`datago_service_key` SecureString)·`ecs.tf`(task def secrets에 `DATA_GO_KR_SERVICE_KEY` 추가, ignore_changes라 다음 수동 rev 등록 때 실제 반영)·`outputs.tf`(`ingest_schedule_name`·`ingest_schedule_state`). 문서 `docs/adr/0011-scheduled-public-data-sync.md`(신규)·`docs/adr/README.md`(테이블 추가)·`docs/adr/0006-study-space-coverage-expansion.md`(착수순서 §7 업데이트)·`TROUBLESHOOTING.md`(TS-019).
- 다음(오케스트레이터가 검토 후 실행 — 아래 최종 보고의 "terraform plan 요약/apply 명령/추가 SSM 파라미터명" 참고): ① `.local/datago.env`의 `DATA_GO_KR_SERVICE_KEY` 값을 `TF_VAR_datago_service_key`로 `terraform apply`. ② 라이브 태스크데프에 `DATA_GO_KR_SERVICE_KEY` secret 추가한 새 rev 등록 + `update-service --force-new-deployment`(container_definitions가 ignore_changes라 apply만으론 라이브에 안 실림, S3_BUCKET_NAME 때와 동일 절차). ③ 스케줄 실측 검증(`aws scheduler get-schedule` + 1회 수동 트리거 또는 임시 즉시-cron으로 실행해 RunTask가 실제로 도는지 확인) — Universal Target의 `input` JSON은 Terraform이 타입 체크를 못 해주는 자유 형식이라 여기서 실측이 특히 중요. ④ 검증되면 `var.ingest_schedule_enabled=true`로 재적용해 월1회 스케줄 활성화.
- 관련: 브랜치 `feat/scheduled-sync-p3`, ADR-0011, TS-019, CLAUDE.md 로드맵 P3(마지막 조각 완료), ADR-0002(멱등)·ADR-0006(soft-delete diff·오픈API 다운로드 경로).
### 2026-07-10 — P4 간판 성능 실증: k6 부하테스트 + EXPLAIN 인덱스 튜닝 (브랜치 `feat/k6-load-explain-p4`, ADR-0012)
- 한 일: CLAUDE.md §10 P4 첫 산출물 "k6 부하테스트 + EXPLAIN 인덱스 튜닝"을 완성. **간판(PostGIS 반경/kNN/bounds 대용량 지리검색)이 GiST 인덱스로 실제로 빠른지를 부하 수치 + 실행계획으로 실증**했다. 로컬 colima에 docker-compose PostGIS를 직접 기동(TS-009의 Testcontainers 하네스 이슈와 별개로, 부하·EXPLAIN은 실 PostGIS에 직접 실측), 합성 시드로 places 30만 + reports 21만(유효 1만·만료 20만)을 만들어 (1) 세 공간쿼리 + 스코어드/추천 쿼리의 EXPLAIN ANALYZE로 인덱스 사용 확증, (2) k6 4엔드포인트 부하로 p95/p99·처리량 실측, (3) 튜닝 대상(만료 제보 누적)을 Flyway V8 인덱스 하나로 좁혀 before/after를 측정했다. 신규 `perf/`(k6·seed·explain·RESULTS) + `docs/adr/0010` + V8. **백엔드 코드(엔티티/쿼리/서비스)는 변경 없음** — 튜닝은 인덱스(V8)와 스크립트/문서뿐이라 무회귀.
- 결정 & 이유(why):
  - **공간 인덱스는 추가하지 않았다(EXPLAIN이 이미 최적이라 확인)** — 반경은 `Bitmap Index Scan on idx_places_geom_geography`(geography GiST, ADR-0001), kNN은 `Index Scan using idx_places_geom_geography` + KNN 정렬(actual ~30ms, 웹 검색으로 재확인한 "인덱스 시 `<->` 최대 ~1800배" 특성과 정합 — Crunchy Data/PostGIS 워크숍), bounds는 **선택적 박스**에서 `idx_places_geom`(geometry GiST) 실측 1.7ms. "없는 문제를 인덱스로 덮지 않는다"(§0-2·§0-4)는 원칙대로, 진짜 필요한 곳에만 인덱스를 넣었다.
  - **bounds 대박스의 Seq Scan은 버그가 아니라 조사 후 "정상"으로 판정** — 밀집 대박스(서울 도심) + `LIMIT 100` + `ORDER BY` 없음이면 플래너가 조기종료 Seq Scan을 고른다(시드 70%가 수도권이라 첫 ~9천 행에서 100건 충족). 희소/작은 박스에서는 geometry GiST를 실제로 탄다(실측). §0-4의 취지는 "전체스캔 방지"지 "무조건 인덱스"가 아니므로 힌트로 강제하지 않았다. 이 판별을 위해 sparse/dense 박스를 각각 EXPLAIN으로 대조(TS-021에 사고 기록).
  - **Flyway V8 = `CREATE INDEX idx_reports_expires ON reports (expires_at)`(유일한 튜닝)** — 스코어드/추천 쿼리는 매번 `place_report_signals` 뷰(ADR-0007)를 LEFT JOIN 하고, 뷰는 `WHERE r.expires_at > now()`로 유효 제보만 집계한다. 제보는 휘발성이라 만료 행이 계속 쌓이는데(물리 삭제 안 함), 플래너가 GROUP BY를 통과해 place_id를 push-down 못 하므로 뷰는 매 쿼리 통째로 집계된다 → 인덱스 없으면 이 필터가 reports 전체를 Seq Scan(만료행 Filter 제거). 비용이 "누적 총 제보 수"에 비례하는 유일한 확장성 위험이었다. 실측(만료 20만): 뷰 빌드 **256→133ms**, 반경 스코어드 **361→172ms** — `Seq Scan on reports (Rows Removed: 200000)` → `Bitmap Index Scan on idx_reports_expires`. **부분 인덱스(`WHERE expires_at > now()`)가 아니라 전체 btree인 이유**: now()는 IMMUTABLE이 아니라 부분 predicate에 못 넣는다(전체 btree면 `expires_at > now()` 범위스캔으로 동일 목적 달성, 유효 비율이 낮을수록 이득↑ = 누적 시나리오에 정확히 부합). §0-4 "전체스캔 금지"를 뷰의 시간축에 적용.
  - **k6 임계는 p95/p99에, CI/느린 인프라 여유 포함** — 웹 검색(2026)으로 확인한 k6 베스트프랙티스: 평균이 아니라 p95/p99에 threshold(꼬리 지연은 평균이 숨긴다), CI/느린 인프라는 프로덕션보다 임계 2~3배 완화. 이 환경(emulated PostGIS)이 정확히 "느린 인프라"라 임계를 "인덱스가 실제로 서빙 중임을 지키는 회귀 가드" 수준으로 잡았다(GiST가 빠지면 이 관대한 임계도 뚫린다). 게이밍으로 무조건 통과시키지 않고 에뮬레이션 한계를 ADR에 명시.
  - **ramping-vus(닫힌 모델) 채택, arrival-rate는 확장점** — 트렌드는 "회귀는 arrival-rate가 정석"(코드가 느려지면 vus 모델은 트래픽을 줄여 회귀를 가릴 수 있음)이라 하지만, 이 환경은 CPU가 포화(4/8/12/30 VU 모두 ~10 RPS로 처리량 평평, 지연만 증가)하므로 열린 모델은 VU가 무한 적체돼 무의미하다. 닫힌 모델이 자원 상한을 정직하게 드러낸다. `PEAK_VUS` env로 조절 가능하게 두고, 정식 arrival-rate 회귀 게이트는 네이티브 CI 하드웨어가 붙는 P4 후속에 additive로 남김.
  - **에뮬레이션 한계 정직 명시(추정 수치 날조 금지, §0-B)** — arm64 맥의 colima(2 vCPU)에서 amd64 PostGIS 이미지가 qemu 에뮬레이트되므로 절대 지연은 네이티브 RDS보다 부풀려져 있다. ADR/README/스크립트 주석에 "읽을 값은 실행계획(인덱스 사용)·before/after 비율·동일 환경 처리량 상한뿐, 프로덕션 수치는 미측정"이라고 못박았다.
- 검증: **k6 green(exit 0)** — PEAK_VUS=4 warm run에서 kNN p95 213ms(med 85) · bounds p95 493ms(med 221) · 반경 p95 1.40s(med 515) · 추천 p95 1.24s(med 726), 실패율 0%(564/564 체크), ~8 RPS. 스모크(`--vus 1 --duration 5s`) 100% 체크 통과. EXPLAIN은 30만 데이터에 실 PostGIS로 실행해 인덱스 사용 캡처. **V8은 로컬에서 백엔드 재기동 시 Flyway가 실제 적용됨을 확인**(`flyway_schema_history` version=8 success=t, `idx_reports_expires` 생성 확인). 백엔드 코드 무변경이라 `check`는 문서/스크립트/마이그레이션만 — CI가 실 PostGIS로 V8 포함 마이그레이션 + 기존 IT를 재검증한다.
- 산출물: `perf/k6/spatial_load.js`(부하 시나리오)·`perf/seed/seed_synthetic_places.sql`(합성 30만 시드)·`perf/explain/explain_spatial_queries.sql`(EXPLAIN)·`perf/explain/RESULTS.md`·`perf/README.md`(재현) · `backend/.../db/migration/V8__reports_expires_index.sql`(신규 인덱스) · `docs/adr/0012-k6-load-explain-index-tuning.md`(신규) · `docs/adr/README.md`(인덱스 갱신).
- 다음: P4 나머지 — ECS Service Auto Scaling(이 부하와 묶어), 실시간 이벤트(제보 급증), 관측성(OTel/Grafana), 네이티브 CI에서 arrival-rate 정식 회귀 게이트.
- 관련: 브랜치 `feat/k6-load-explain-p4`, ADR-0012, TS-021, `perf/*`, `V8__reports_expires_index.sql`, CLAUDE.md §0-4·§7·§10 P4·§11.

### 2026-07-10 — ECS Service Auto Scaling (P4 심화, 브랜치 `feat/ecs-autoscaling-p4`, ADR-0013)
- 한 일: CLAUDE.md §7·로드맵 P4가 지정한 "오토스케일링/HPA=ECS Service Auto Scaling, k6 부하테스트와 함께 additive" 조각. 별도 파일 `infra/terraform/autoscaling.tf`에 `aws_appautoscaling_target`(ECS 서비스, min/max)과 `aws_appautoscaling_policy`(CPU target tracking)만 추가 — 기존 `ecs.tf`는 `ignore_changes` 주석 한 줄만 보강했다(과설계 금지, 다른 브랜치와 충돌 최소화 지시 준수).
- 결정 & 이유(why) — 상세 근거는 [ADR-0013](./docs/adr/0013-ecs-service-autoscaling.md)에 전부 기록, 요지만:
  - **지표 = CPU 이용률(`ECSServiceAverageCPUUtilization`), ALB `RequestCountPerTarget` 아님.** 2026-07 웹검색으로 AWS 공식 문서·실무 가이드를 확인한 결과 일반 웹서비스는 요청수 지표가 더 권장되는 경향이 있지만, 그늘의 ECS 앱 계층은 두 이유로 예외다: ① 태스크가 이미 0.25vCPU(`var.task_cpu=256`)로 얇아 CPU 포화가 요청수 스파이크보다 먼저 온다. ② `/places`(bounds)·`/places/{id}`(survival 조립)·`/recommendations`(2단 재랭킹)·AI 요약(외부 I/O)이 요청당 CPU 비용이 크게 달라 균질 목표값이 필요한 RequestCountPerTarget과 안 맞는다. PostGIS 반경/kNN 연산 자체는 RDS에서 도는 별개 리소스라 이 정책의 스케일 대상(ECS 앱 계층)과 무관 — 여기서 재는 CPU는 Jackson 직렬화·JTS 좌표 변환 등 앱 계층 자체의 포화다. target=60%(AWS 권장 50~70% 중간값).
  - **min=1·max=`var.autoscaling_max`(기본 3), 기본 ENABLED.** max=3이면 최악의 경우도 베이스라인(~$12/월) 대비 최대 3배(~$36/월)로 유계. ADR-0011(공공데이터 스케줄)이 기본 DISABLED였던 것과 상반된 선택인데, 위험 성격이 다르기 때문 — ADR-0011의 위험은 "사람 없이 soft-delete까지 도는 비가역·조용한 데이터 변경"이었지만 이 기능의 위험은 "유계·가역적 비용"뿐이라 같은 안전장치를 복제할 이유가 없다고 판단했다(그래도 `var.autoscaling_enabled=false`로 즉시 끌 수 있는 스위치는 동일하게 제공).
  - **쿨다운 = scale-out 60s(AWS 권장 그대로) / scale-in 600s(AWS 흔한 관례 300s의 2배).** 그늘의 실트래픽은 정상 성장 곡선이 아니라 k6 부하테스트·수동 데모 같은 짧고 뾰족한 버스트라, 버스트 꼬리에서 1↔2 태스크로 플래핑하는 걸 막으려면 더 넉넉한 쿨다운이 안전하다(오케스트레이터 지시: "스케일인 쿨다운 넉넉히").
  - **`desired_count`가 오토스케일러와 안 싸우는 배선은 이미 돼 있었다** — `aws_ecs_service.app`의 `lifecycle.ignore_changes`에 `desired_count`가 기존부터 포함(과거 배포 후 수동 조정 목적으로 추정). 새 코드 없이 그대로 재사용, 의도만 주석으로 보강.
  - **IAM 리소스 불필요** — `aws_appautoscaling_target`/`_policy`는 AWS Application Auto Scaling의 서비스연결역할을 최초 사용 시 자동 생성해 쓴다(ADR-0011의 EventBridge Scheduler는 사람이 만든 커스텀 실행 롤이 필요했던 것과 대조).
- 검증: `terraform fmt`(자동 정렬 적용, 이후 `-check` clean) → `terraform init -backend=false`(provider v5.100.0 재사용) → `terraform validate` **Success**. 로컬에 실 tfstate·tfvars·AWS 자격증명 컨텍스트가 없어(이 워크트리는 별도 체크아웃) `terraform plan`은 의미 있는 실행이 아니라 생략(오케스트레이터 지시대로 validate/fmt 위주) — 예상 리소스·비용은 아래 최종 보고에 수기로 정리. 백엔드·Flyway 무변경(순수 인프라 스코프)이라 `./gradlew check` 재실행은 생략.
- 산출물: `infra/terraform/autoscaling.tf`(신규)·`variables.tf`(`autoscaling_enabled`·`autoscaling_max`)·`ecs.tf`(주석 1줄)·`outputs.tf`(`autoscaling_status`). 문서 `docs/adr/0013-ecs-service-autoscaling.md`(신규)·`docs/adr/README.md`(테이블 추가).
- 다음(오케스트레이터가 검토 후 실행): ① `terraform apply`(아래 최종 보고의 plan 요약 참고 — 신규 리소스만 add, 기존 리소스 변경 없음). ② apply 직후 CloudWatch/ECS 콘솔에서 `autoscaling_status` 아웃풋과 실제 태스크 수 확인. ③ D1(k6 부하테스트) 실행 시 CPU 60% 이상이 600초 넘게 유지되는지, 그에 따라 태스크가 2~3개로 늘었다가 부하 종료 후 600초 뒤 1개로 복귀하는지 실측 관찰 — 이번 스코프는 Terraform 코드까지이고 실부하 검증은 D1과 함께 이뤄진다(설계상 짝 구성, ADR-0013 명시).
- 관련: 브랜치 `feat/ecs-autoscaling-p4`, ADR-0013, CLAUDE.md §7(오토스케일링 P4 additive 지시)·§0.2(과설계 금지)·로드맵 P4, ADR-0011(위험 성격 비교 기준점), D1(k6 부하테스트, 짝 산출물).

### 2026-07-10 — P4 관측성: Micrometer/Prometheus + Boot 4 OTel 스타터(트레이싱) + 로컬 Grafana/Tempo (브랜치 `feat/observability-otel-p4`, ADR-0014)
- 한 일: 로드맵 P4 마지막 심화 조각 "관측성(OTel/Grafana)"를 마무리했다. 착수 전 `application.yml`을 읽다가 `management.endpoints.web.exposure.include: health,info,prometheus`가 이미 하드코딩돼 있는 걸 발견하고 실제 프로덕션 ALB URL로 확인한 결과, **`/actuator/prometheus`가 이미 인증 없이 공개돼 있었다**(TS-022) — 이번 작업의 첫 결정은 그래서 "새 기능 추가"가 아니라 "이미 라이브에 있는 노출 구멍을 닫는 것"이었다. 그 위에 ① Boot 4.0 공식 OTel 스타터로 트레이싱을 추가하고 ② 간판(반경 ST_DWithin·kNN `<->`) 공간쿼리에 커스텀 latency Timer 2개를 계측하고 ③ docker-compose `observability` 프로필(Prometheus+Grafana+Tempo)을 additive로 얹었다.
- 결정 & 이유(why) — 상세 근거는 [ADR-0014](./docs/adr/0014-observability-otel-micrometer-grafana.md)에 전부 기록, 요지만:
  - **`/actuator/prometheus` 노출 = 프로덕션 기본 미노출(옵트인), 관리 포트 분리·별도 인증 계층은 안 만든다.** `management.endpoints.web.exposure.include`를 `${MANAGEMENT_EXPOSURE:health,info}`로 바꿨다 — ECS가 이 env를 안 주므로 프로덕션은 항상 이 안전한 기본값이다. 관리 포트 분리(`management.server.port`)는 ALB target group이 8080 하나만 바라보고 `/actuator/health`가 바로 그 포트에서 헬스체크에 응답해야 해 SG·target group 변경(인프라 apply)이 필요해져 지시 제약("ECS/인프라 변경은 코드/문서만")과 충돌한다고 판단해 기각 — 옵트인은 코드 레벨에서 즉시 닫히면서 인프라 변경이 전혀 없다. 이 레포에 이미 확립된 "값 미설정=안전한 기본" 패턴(`GEUNEUL_PROXY_SECRET`·`OPENROUTER_API_KEY`)과도 정합된다.
  - **트레이싱 프로바이더 = `org.springframework.boot:spring-boot-starter-opentelemetry`(Boot 4.0 공식 스타터, 2025-11 출시).** CLAUDE.md §7이 "micrometer-tracing-bridge-otel + OTLP exporter, 또는 Boot의 OTel 스타터" 둘 다 허용해, Maven Central에서 이 프로젝트의 정확한 Boot 버전(4.0.6)으로 존재함을 POM 직접 확인 후 채택했다 — 세 의존성(micrometer-tracing-bridge-otel·opentelemetry-exporter-otlp·micrometer-registry-otlp)을 수동으로 버전 맞추는 대신 스타터 하나로 Boot BOM이 정합을 보장한다. 프로퍼티 이름(`management.opentelemetry.tracing.export.otlp.endpoint` 등)은 문서가 아니라 **실제 4.0.6 아티팩트의 `spring-configuration-metadata.json`을 직접 열어** 확인했다(버전 드리프트 리스크 회피).
  - **메트릭은 Prometheus(pull) 1순위, OTLP 메트릭(push)은 옵트인으로 꺼둔다** — 같은 지표를 두 경로로 중복 발행하는 걸 피했다. 트레이싱만 필연적으로 push(OTLP)다.
  - **커스텀 메트릭 2개 = `geuneul.place.search.radius`/`geuneul.place.search.nearest`(category 태그).** `PlaceSearchService`에 `MeterRegistry`를 주입해 `Timer.Sample`로 **PostGIS 쿼리 실행 구간만**(앱 매핑·날씨 호출은 제외) 감쌌다 — CLAUDE.md 핵심 차별점("PostGIS 대용량 지리검색 반경/kNN")을 그대로 계측 대상으로 삼았고, ADR-0012(k6)가 이미 이 두 경로의 p95/p99를 외부 관점에서 보고하므로 내부 관점(Micrometer)을 같은 백분위 기준으로 맞춰 교차검증 가능하게 했다. `category` 태그는 `PlaceCategory` enum(고정 카디널리티)만 써 Prometheus 카디널리티 폭발을 피했다. `management.metrics.distribution.percentiles-histogram`을 이 둘 + `http.server.requests`에만 켜 히스토그램 버킷(→ `histogram_quantile` 가능)을 확보하면서 저장 비용은 최소화했다.
  - **로컬 스택 = Prometheus + Grafana + Tempo(single-binary, OTel Collector 없이 백엔드가 직접 push), docker-compose `observability` 프로필(옵트인, 기존 `docker compose up` 흐름 무변경).** Tempo 설정은 `grafana/tempo` 공식 예제를 이 레포 규모로 축소(k6 합성 트래픽·Alloy·vulture·서비스그래프 remote-write는 과설계라 제외). Grafana는 로컬 전용 익명 Admin(로그인 절차 생략, 절대 프로덕션 미노출 — 프로필 자체가 옵트인이고 ECS에도 존재하지 않음)으로 대시보드 1개(`geuneul-overview.json`, 6패널: 반경/kNN p95·HTTP 처리율/p95·JVM 힙·CPU)를 자동 프로비저닝한다.
  - **프로덕션 반영은 전혀 없다** — ECS 태스크데프·SSM은 이번 PR에서 안 건드렸다. 관리형 트레이스 백엔드 연결 방법은 ADR-0014 §5에 "이렇게 붙인다"만 문서화(다른 비밀들과 동일 패턴으로 `OTEL_EXPORTER_OTLP_ENDPOINT`를 ECS secrets에 추가하면 됨).
- 검증(실 Docker로 전체 루프 실측 — 이 맥의 colima Docker 데몬은 살아있고 `docker`/`docker-compose` CLI가 정상 동작함을 이번에 확인, TS-009는 Testcontainers의 docker-java 라이브러리 문제이지 Docker 자체 문제가 아님을 재확인):
  1. `./gradlew clean check` 로컬 green — 신규 유닛 `PlaceSearchServiceTest`(+2건, `SimpleMeterRegistry`로 커스텀 타이머가 category 태그와 함께 정확히 1회 기록됨을 검증) 전부 통과. 신규 IT(`ActuatorExposureIT` 3건·`ActuatorPrometheusOptInIT` 2건)는 로컬 colima Testcontainers skip(TS-009 패턴) — 대신 아래 4)로 실동작을 직접 검증.
  2. `docker-compose --profile observability up -d prometheus grafana tempo` 실제 기동 — 세 컨테이너 모두 정상 기동, Grafana API로 데이터소스(Prometheus `OK`)·대시보드(6패널 정상 파싱) 로드 확인.
  3. 임시 PostGIS/Redis 컨테이너(별도 포트, 커밋 대상 아님) + `./gradlew bootJar` 산출물을 `MANAGEMENT_EXPOSURE`/`OTEL_*` env로 직접 기동해 **엔드투엔드 실측**: `/actuator/prometheus`에서 `geuneul_place_search_radius_seconds_bucket`/`geuneul_place_search_nearest_seconds_bucket` 히스토그램이 실제 요청 후 나타남 → Prometheus 타깃이 `host.docker.internal:8080`을 `up`으로 스크레이프 → Grafana 데이터소스 프록시로 대시보드 패널 쿼리(`histogram_quantile(0.95, ...)`)가 실제 latency 숫자를 반환 → Tempo `/api/search`에 `rootServiceName=geuneul`인 실 트레이스(`http get /places` 등) 수집 확인.
  4. **같은 env 없이(기본값) 재기동해 `ActuatorExposureIT`의 계약을 수동으로 재현**: `/actuator/health`=200, `/actuator/prometheus`=404, `/actuator/env`=404 — CI에서 이 IT가 어떤 결과를 낼지 사전 검증.
  5. 로컬 검증 중 PostGIS init 스크립트 레이스(TS-023)를 만나 원인을 좁혀 기록.
- 산출물: 백엔드 `build.gradle`(spring-boot-starter-opentelemetry)·`application.yml`(management.endpoints/metrics/tracing/opentelemetry/otlp 블록)·`domain/place/PlaceSearchService`(MeterRegistry 주입 + `timed()` 헬퍼) · 테스트 `PlaceSearchServiceTest`(+2)·`global/observability/ActuatorExposureIT`(신규)·`global/observability/ActuatorPrometheusOptInIT`(신규) · 로컬 스택 `docker-compose.yml`(observability 프로필: prometheus·grafana·tempo)·`observability/`(신규 디렉터리 — `prometheus/prometheus.yml`·`tempo/tempo.yaml`·`grafana/provisioning/{datasources,dashboards}`·`grafana/dashboards/geuneul-overview.json`) · 문서 `docs/adr/0014-observability-otel-micrometer-grafana.md`(신규)·`docs/adr/README.md`(인덱스)·`TROUBLESHOOTING.md`(TS-022·TS-023).
- 다음: 관리형 트레이스 백엔드 연결(원할 때, ADR-0014 §5 절차)·실시간 이벤트(제보 급증 알림). CLAUDE.md 로드맵 P4의 "OTel/Grafana" 항목은 이걸로 완료 — ECS Service Auto Scaling은 이 작업 중 별도 세션(#39)이 이미 병합해 P4 남은 항목에서 제외.
- 관련: 브랜치 `feat/observability-otel-p4`, ADR-0014, TS-022, TS-023, CLAUDE.md §0-2(과설계 금지)·§7(Test/Ops)·§10 P4, ADR-0012(같은 반경/kNN 경로의 p95/p99를 k6가 이미 외부 관점에서 측정 — 이번 Micrometer 계측이 내부 관점으로 교차검증).

### 2026-07-10 — AI 요약 프로바이더 설정 중립화(리네임) + Mistral 전환 (브랜치 `feat/ai-provider-neutral-rename`, ADR-0010 갱신)
- 한 일: ADR-0010(P3 AI 한줄 요약)에서 이름이 특정 프로바이더(OpenRouter)에 고정돼 있던 걸 실체(OpenAI 호환 범용 클라이언트)에 맞춰 중립 리네임했다. `domain/ai/OpenRouterClient` → `ChatCompletionClient`(파일명·클래스명·Javadoc·생성자 전부), 테스트 `OpenRouterClientTest` → `ChatCompletionClientTest`, `application.yml`의 `ai.openrouter.*` → `ai.summary.*`(env `OPENROUTER_API_KEY/BASE_URL/MODEL` → `AI_SUMMARY_API_KEY/BASE_URL/MODEL`), `infra/terraform`의 `openrouter_api_key` 변수/SSM 리소스/ECS secret → `ai_summary_api_key`. 같은 PR에서 프로바이더 기본값도 Mistral로 교체(base-url `https://api.mistral.ai/v1`, model `mistral-small-latest`) — **동작/로직 변경 없음, 순수 리네임 + 기본값 변경**(타임아웃·MAX_TOKENS·프롬프트·캐시·graceful degradation 전부 그대로).
- 왜(why): OpenRouter 무료 티어가 플랫폼 전반에서 rate-limit(429)에 걸려 데모 출력이 막혔다 — 코드는 처음부터 `/chat/completions`만 가정하는 OpenAI 호환 범용 클라이언트였고 base-url·key·model이 전부 설정값이라, 실제 전환은 config만 바꾸면 끝났다. 다만 클래스명·설정 프리픽스가 여전히 `OpenRouter`로 남아 있으면 "실제로는 Mistral을 쓰는데 이름은 OpenRouter"라는 이름-실체 불일치가 생겨 다음에 이 코드를 읽는 사람이 오해할 소지가 있었다 — 그래서 프로바이더 전환과 함께 이름도 중립화했다.
- 검토한 대안: ① **OpenRouter 유료키로 전환** — 곁다리 기능에 기본값으로 비용을 발생시키는 건 "AI는 곁다리" 원칙과 어긋나 기각. ② **SambaNova 채택** — 실측 결과 정상 응답은 확인됐으나, 동일 시스템 프롬프트 비교에서 침수 위험 순화 표현 품질이 Mistral보다 낮아 후순위. ③ **기존 이름(`OpenRouterClient`/`ai.openrouter.*`) 유지, 프로바이더만 교체** — 변경 범위가 작아 빠르지만 이름-실체 불일치가 남는다는 단점이 있어, 사용자가 중립 리네임을 명시적으로 선택했다.
- 트렌드 근거: 2026 현재 OpenAI Chat Completions 스키마(`/chat/completions`, `messages`/`max_tokens`/`temperature` 필드)가 사실상 업계 표준 인터페이스로 굳어져, Mistral·Groq·DeepInfra·SambaNova·OpenRouter 등 대다수 프로바이더가 이 스키마를 그대로 호환한다 — 그래서 프로바이더 스왑이 SDK 교체 없이 base-url 한 줄 교체로 끝난다. 이번 리네임은 그 사실을 코드/설정 이름에도 반영한 것뿐이다.
- 검증: `./gradlew compileJava compileTestJava` green, `./gradlew test`(domain.ai 패키지 유닛) green — `ChatCompletionClientTest`(8)·`AiSummaryServiceTest`(6)·`AiSummaryCacheProxyTest`(2) 전부 통과(Testcontainers IT는 이 머신 colima 이슈로 skip, TS-009 패턴). `grep -rni openrouter backend/src infra/terraform/*.tf` **0건** 확인(tfstate/tfvars 제외).
- 산출물: `domain/ai/ChatCompletionClient.java`(리네임)·`domain/ai/AiSummaryService.java`(참조 갱신)·테스트 `ChatCompletionClientTest.java`(리네임)·`AiSummaryServiceTest.java`·`AiSummaryCacheProxyTest.java`(참조 갱신)·`application.yml`(`ai.summary.*`)·`infra/terraform/ssm.tf`·`variables.tf`·`ecs.tf`(`ai_summary_api_key`, ecs.tf에 `AI_SUMMARY_BASE_URL`/`AI_SUMMARY_MODEL` 비-시크릿 env 추가)·`docs/adr/0010-ai-summary-openrouter-provider.md`(갱신, §4 신설).
- 다음(오케스트레이터가 검토 후 실행): ① `terraform apply`로 `ai_summary_api_key` SSM 파라미터 재생성(기존 `openrouter_api_key`는 리소스명이 바뀌어 destroy+create로 처리됨 — 값은 tfvars에서 새로 주입). ② 라이브 태스크데프에 `AI_SUMMARY_API_KEY`/`AI_SUMMARY_BASE_URL`/`AI_SUMMARY_MODEL`이 반영된 새 rev 등록(describe→env/secret 교체→register→update-service). ③ 유효한 Mistral 키를 SSM에 넣고 실측(제보 있는 place 상세에서 `aiSummary` 비-null 확인).
- 관련: 브랜치 `feat/ai-provider-neutral-rename`, ADR-0010(갱신), CLAUDE.md §0-B(의사결정 프로토콜)·§8(AI 스택, Claude API 기본에서 이탈 근거 갱신)·규칙 D(비밀은 .local/.env로만, 실제 키 하드코딩 금지).

### 2026-07-10 — AI 요약 라이브 배포(Mistral 전환 실행·검증) — PR #41 후속 배포 단계
- 한 일: 위 리네임 PR(#41) 머지 후 실제 프로덕션 전환을 완료했다. ① `terraform.tfvars`(gitignore) 변수 `openrouter_api_key`→`ai_summary_api_key`로 교체 + Mistral 키 주입 → `terraform apply`(SSM 파라미터 `/geuneul/openrouter_api_key` destroy + `/geuneul/ai_summary_api_key` create, 1 add·1 destroy). ② 머지가 트리거한 자동배포(rev40, 신 이미지+구 `OPENROUTER_API_KEY` env — 이 시점 aiSummary는 graceful null)가 stable해진 걸 확인한 뒤, rev40을 베이스로 secret `OPENROUTER_API_KEY`→`AI_SUMMARY_API_KEY`(신 파라미터 ARN) 교체 + env `AI_SUMMARY_BASE_URL=https://api.mistral.ai/v1`·`AI_SUMMARY_MODEL=mistral-small-latest` 추가한 **rev41 수동 등록** → `update-service --force-new-deployment` → `wait services-stable`로 안정화 확인. ③ 실측: `GET /places/185`(유효 제보 1건 COOL) → `aiSummary="시원하다는 제보가 최근에 있습니다."`(Mistral 생성, 비-null). 목록 `/places`는 설계대로 aiSummary=null(상세 getById 전용). ECS 로그에 `[ai]` warn/ERROR 없음(성공은 무로그).
- 왜(배포 순서): 자동배포 rev40(구 파라미터 참조)이 stable해지기 **전에** terraform이 구 SSM 파라미터를 파괴하면 rev40 태스크 기동이 실패할 수 있어, "rev40 stable 확인 → terraform apply(파라미터 교체) → 즉시 rev41 등록" 순으로 파괴-창을 최소화했다. min=1·무부하라 실행 중 태스크는 env가 이미 주입돼 영향 없고, apply와 rev41 등록을 back-to-back으로 처리해 재기동 창을 닫았다.
- 검토한 대안: SSM 파라미터를 destroy 없이 신규 추가만 하고 구 파라미터를 rev41 전환 후 별도 apply로 지우는 2단 apply(파괴-창 완전 제거)도 가능했으나, tfvars/코드가 이미 리네임돼 있어 terraform이 구 리소스를 자동 파괴하는 게 상태-현실 정합상 더 단순 — 무부하 창의 실질 리스크가 낮아 단일 apply를 택했다.
- 산출물(인프라, 코드/문서 변경 없음): SSM `/geuneul/ai_summary_api_key`(SecureString, Mistral 키) · 태스크데프 **rev41**(라이브) · `.local` tfvars(gitignore) 갱신. 라이브 rev는 `ignore_changes`라 terraform이 아닌 수동 rev로 반영(HANDOFF 패턴).
- 관련: PR #41, ADR-0010(§4), TS-024(tfvars 멀티라인 함정), CLAUDE.md 규칙 A(신원)·D(비밀 .local/SSM만)·HANDOFF 운영 치트시트(AI 프로바이더 교체 복붙 갱신).

### 2026-07-10 — ALB 무료 HTTPS: CloudFront 기본 도메인(ADR-0015) + EventBridge 스케줄 활성화 + 화장실 재적재
- 한 일(3건, 로드맵 후속 "외부 스위치"):
  - **① EventBridge 주기동기화 활성화(#42)**: 스케줄이 쓰는 것과 동일한 입력(`--ingest.source=library --deactivate-stale --exit-after`)으로 수동 `ecs run-task` 1회 실검증 → exitCode=0, `[library-api] fetched=3555 upserted=3551 geocoded=145 deactivated=0 featuresBackfilled=6828`, Flyway V8 정합. `terraform apply -var ingest_schedule_enabled=true`로 State=ENABLED, 이후 variables.tf default를 true로 승격(repo=live 정합, 되돌림 방지).
  - **② 화장실 지오코딩 실패 재시도**: `prod-ingest.sh public_toilet`(toilets.csv 59,768행 + KAKAO 지오코딩) 재실행 — 멱등이라 이미 좌표 있는 46,897건은 재사용 스킵, **실패 7,193건 중 5,437건 신규 지오코딩 성공**(`upserted=5437 geocodeReused=46897 geocodeFailed=1756`). 총 화장실 좌표 46,897 → 52,334건. 남은 1,756건은 카카오가 못 푸는 옛 지번·산번지·공백없는 주소(데이터 품질 한계, 재시도 무의미). 창원 3km=58건 라이브 확인.
  - **③ ALB 무료 HTTPS(ADR-0015)**: 통제 도메인이 없어 ACM 공개 인증서를 못 받는 상황(2026 무료 도메인 사실상 폐지) → CloudFront 배포를 ALB 앞에 두고 기본 도메인(`*.cloudfront.net`)의 AWS 관리 인증서로 즉시 HTTPS. `cloudfront.tf`(오리진=ALB http-only, redirect-to-https, Managed-CachingDisabled+AllViewer로 캐시 없는 관리형 HTTPS 프록시). 실측: `https://d2pedv974beobb.cloudfront.net` health 200(TLS 검증 통과)·/places 30건·http→301·swagger 200.
- 왜: 전부 "외부 키/스위치" 잔여 항목. HTTPS는 도메인 부재라 CloudFront 기본 도메인이 유일한 무료·즉시 경로(§0-B 웹 확인, ADR-0015 대안표). 스케줄·화장실은 콘솔 없이 바로 가능한 것부터.
- 블로커(사용자 콘솔 필요, 자율 불가): **쉼터 전국** — 전국무더위쉼터 표준데이터 API가 `resultCode 12`(미승인/폐기), safetydata 전용 키 없음. **상권정보 STUDY_CAFE/CAFE** — 같은 datago 키로 library=정상(resultCode 00)인데 상가업소 API(B553077)만 **403 Forbidden** = 활용신청 미승인. 둘 다 data.go.kr/safetydata 콘솔에서 사용자가 활용신청 승인받아야 풀림.
- 산출물: `infra/terraform/cloudfront.tf`(신규)·`outputs.tf`(https_url)·`variables.tf`(ingest default true)·`docs/adr/0015-*.md`(신규)·`docs/adr/README.md`(0015 색인+0010 제목 정정). 라이브: CloudFront 배포 E2WO35VKYGKAGX, 스케줄 ENABLED, 화장실 52,334.
- 관련: PR #42(스케줄), CloudFront PR, ADR-0015, CLAUDE.md §7·§10 P4·§0-B, HANDOFF 남은목록.

### 2026-07-10 — 실시간 제보 급증 알림: Postgres LISTEN/NOTIFY → SSE (브랜치 `feat/realtime-report-surge-p4`, ADR-0016)
- 한 일: 로드맵 P4 간판의 마지막 미완 조각 "실시간 이벤트(제보 급증 알림)"를 구현했다. 두 레이어로 분리 — **감지(간판·시공간 SQL)** + **전파(LISTEN/NOTIFY→SSE)**. ① 감지: `place_report_signals`와 같은 정신모델의 순수 건수 쿼리(`ReportSurgeRepository.countRecent`/`findSurge`/`findSurgingInBounds`) — "최근 windowMinutes(기본10) 분 안에 유효(미만료) 제보 ≥ minReports(기본3)"인 장소를 판정, bounds는 `places`를 `geom && ST_MakeEnvelope`(V2 GiST)로 공간조인. `mode() WITHIN GROUP`으로 최빈 제보 타입까지 뽑아 안내 문구 생성. ② 전파: Flyway **V9**가 `reports` AFTER INSERT 트리거로 `pg_notify('geuneul_report_surge', place_id)` — 각 앱 인스턴스가 전용 커넥션으로 LISTEN(`ReportNotificationListener`, SmartLifecycle 백그라운드 스레드, 끊기면 백오프 재연결)하다가 알림을 받으면 급증 재확인 후 SSE 구독자(`SurgeEmitterRegistry`)에 브로드캐스트. API: `GET /alerts/surge?bounds=`(폴백/초기 스냅샷)·`GET /alerts/stream`(SSE, `text/event-stream`) 둘 다 permitAll(공개 커먼스). 기능 플래그 `geuneul.realtime.enabled`(기본 true)로 리스너 격리 — IT에선 false.
- 왜(why): P4 "실시간 이벤트"를 어느 전파 메커니즘으로 할지가 핵심 결정이었다. **LISTEN/NOTIFY**를 골랐다 — 이미 ECS Service Auto Scaling(min1/**max3**, ADR-0013)이 붙어 인스턴스가 최대 3대까지 뜨는데, 제보 insert 인스턴스 ≠ SSE 구독 인스턴스일 수 있어 인프로세스 이벤트(`ApplicationEventPublisher`)로는 전파가 끊긴다. LISTEN/NOTIFY는 어느 인스턴스가 insert하든 전 인스턴스가 알림을 받아 이 팬아웃을 **새 인프라 0**(이미 있는 RDS)으로 해결한다. 감지를 앱 메모리 카운터가 아니라 DB SQL로 둔 것도 같은 이유(인스턴스별 카운터는 갈라져 부정확 — 간판=DB 시공간, §5). SSE는 단방향 서버→클라 알림에 정확히 맞고 HTTP라 BFF(ADR-0004)·CloudFront(ADR-0015)를 그대로 통과·자동 재연결.
- 검토한 대안: ① **Kafka** — 브로커 운영 부담이 저빈도 알림에 과설계, CLAUDE.md §7이 명시적 금지("필요 입증 후에만"). ② **Redis Streams** — Redis가 "지워도 되는 선택적 캐시"(ADR-0009)라 알림을 결합시키면 그 성질이 깨짐 + 소스오브트루스(PG)에 트리거로 걸면 제보 트랜잭션과 원자적(유실 창 0). ③ **인프로세스 ApplicationEvent만** — max3 오토스케일링에서 타 인스턴스 구독자 전파 불가. ④ **WebSocket** — 양방향이라 단방향 알림엔 과설계. ⑤ **순수 폴링만** — 실시간성 부족(단, SSE 폴백 겸 스냅샷으로 `/alerts/surge`는 남김).
- 트렌드 근거(2026): SSE(`EventSource`)가 단방향 서버푸시의 표준(WebSocket 대비 경량, 브라우저 자동 재연결 내장). Postgres LISTEN/NOTIFY는 "이미 DB가 있을 때" 경량 실시간 팬아웃의 정석 — 제보 트랜잭션과 같은 커밋에 묶여 유실 창이 없고 페이로드(place_id 하나)가 8000바이트 제한과 무관. CLAUDE.md §7의 "Redis Streams / Postgres LISTEN·NOTIFY" 지시에 정합.
- 표현 규율(§6): 급증 안내 문구(`SurgeInfo.message`)는 공포 조장 금지 — 침수·미끄럼 위험 계열도 "위험!"이 아니라 "최근 침수 제보가 몰리고 있어요 · 우회를 권장해요" 톤으로 순화(단위테스트로 "위험!" 미포함 검증). 백엔드는 사실(placeId·count·최빈타입)만 싣고 최종 톤은 프론트(⑧)가 렌더.
- 검증: 단위 `SurgeInfoTest`(6, 문구 매핑·§6 순화·중복'최근' 버그 수정)·`SurgeEmitterRegistryTest`(3, 등록/집계·죽은 emitter 정리) green. IT `ReportSurgeIT`(임계 경계·만료 제외·시간창 밖 제외·bounds 필터·**NOTIFY 트리거 실발화**)는 실 PostGIS라 CI 위임(로컬 colima skip, TS-009 패턴). `./gradlew compileJava compileTestJava` green. 개발 중 잡은 버그 2건: (a) 기본 문구 "최근" 중복, (b) 갓 만든 SseEmitter는 early-send 버퍼링으로 send가 안 던져 테스트가 `complete()`로 죽은 emitter를 명시해야 정확.
- 산출물: `domain/alert/`(신규 도메인 — `ReportSurgeService`·`ReportSurgeRepository`·`SurgingPlaceView`·`SurgeEmitterRegistry`·`ReportNotificationListener`·`AlertController`·`dto/SurgeInfo`) · `db/migration/V9__report_surge_notify_trigger.sql`(신규) · `application.yml`(`geuneul.realtime.*`) · `AbstractIntegrationTest`(IT에서 realtime off) · 테스트 3파일 · `docs/adr/0016-realtime-report-surge-listen-notify-sse.md`(신규).
- 다음: 프론트(⑧)에서 지도 급증 배지 + EventSource 구독(초기 스냅샷은 `/alerts/surge`). 임계값(window/minReports)은 P5 동작구 필드테스트에서 시딩 밀도에 맞춰 조정.
- 관련: 브랜치 `feat/realtime-report-surge-p4`, ADR-0016, ADR-0013(오토스케일링 — 팬아웃 정당화)·ADR-0007(시공간 신호)·ADR-0009(Redis 선택적 캐시)·ADR-0004(BFF)·ADR-0015(CloudFront), CLAUDE.md §7(실시간 메커니즘)·§5(DB 시공간)·§6(표현 규율)·§10 P4.

### 2026-07-10 — 시간대별 혼잡 파생(자체 popular-times) (브랜치 `feat/popular-times-p4`, ADR-0005 §④)
- 한 일: 외부 API 없이 우리 UGC(reports)만으로 장소별 "요일×시간 혼잡 패턴"을 유도했다. `GET /places/{id}/popular-times` — reports 이력을 **KST 기준** 요일(0=일~6=토)×시간(0~23)으로 집계해 슬롯별 sampleCount(활동량)·crowdedCount·seatOkCount와 혼잡 등급(BUSY/MODERATE/QUIET/UNKNOWN)을 반환. 등급은 순수 함수 `PopularTimesSlot.level` — `crowdScore=(crowded−seatOk)/(crowded+seatOk)` ≥+1/3 BUSY, ≤−1/3 QUIET, 혼잡 신호 없으면 UNKNOWN.
- 왜(why): ADR-0005 §④의 "시간대별 혼잡 파생(자체 popular-times, 외부 API 0)" 항목 — 카공맵류의 "지금 붐비나"를 외부 popular-times API에 의존하지 않고 SEAT_OK/CROWDED 제보 이력으로 자체 유도(간판=우리 데이터·SQL 정합). 두 가지 설계 결정: ① **만료 제보도 포함** — 휘발성 규약(expires_at)은 survival_score 같은 "지금" 신호에만 적용하고, 혼잡 패턴은 "과거 이력의 분포"를 채굴하므로 만료분도 살아있는 데이터다(급증 알림 ①의 '지금 몰리나'와 정반대 시간관). ② **KST 변환** — created_at은 timestamptz(UTC 저장)라 `AT TIME ZONE 'Asia/Seoul'`로 한국 벽시계로 바꾼 뒤 요일/시간을 뽑는다(국내 전용 서비스라 로컬 시간이 사용자 직관과 일치).
- 설계 판단(뷰 vs 파라미터 쿼리): 전역 뷰(place×dow×hour) 대신 **place_id 선필터 파라미터 쿼리**를 택했다 — 상세 화면에서 한 장소만 조회하는 접근 패턴이라 `idx_reports_place_created` 선필터가 전역 뷰 스캔보다 효율적이고, Flyway 마이그레이션도 불필요(스키마 무변경). 집계 SQL 자체가 "SQL 중심"(ADR-0005 §④ 의도)을 만족.
- 검증: 단위 `PopularTimesSlotTest`(4, 등급 경계 BUSY/QUIET/MODERATE/UNKNOWN) green. IT `PopularTimesIT`(3: KST 슬롯 집계·**UTC→KST hour=14 변환 정확성**·토요일 DOW=6·만료 포함·빈 목록)는 실 PostGIS라 CI 게이트. `2026-07-11`이 토요일(DOW 6)임을 `date`로 확인. `compileJava/compileTestJava` green.
- 산출물: `domain/report/PlaceCongestionSlotView`(신규 투영)·`dto/PopularTimesSlot`(신규)·`ReportRepository.congestionByPlace`(네이티브 집계)·`ReportService.popularTimes`·`ReportController`(GET /places/{id}/popular-times) · 테스트 2파일.
- 다음: 프론트(⑧)에서 상세 화면에 요일×시간 히트맵. 시딩 밀도가 낮은 초기엔 UNKNOWN이 많음(P5 필드테스트로 채워짐).
- 관련: 브랜치 `feat/popular-times-p4`, ADR-0005 §④, CLAUDE.md §10 P4(시간대별 혼잡 파생)·§0-3(멱등)·§5(DB 시공간). 급증 알림 ①(ADR-0016)과 시간관이 반대(지금 vs 이력)라는 점을 대비 문서화.

### 2026-07-10 — GPS 방문 인증(verified) — 허위제보 억제 (브랜치 `feat/gps-visit-verify-p4`, ADR-0005 §④ 구현)
- 한 일: ADR-0005 §④의 "간판·차별점" 항목 "GPS 방문 인증"을 구현했다. 제보 생성 시 앱이 보낸 제보자 좌표가 장소 `ST_DWithin(100m)` 이내면 `reports.verified=true`로 남긴다(Flyway **V10** 컬럼 추가, 기본 false). verified 제보는 `place_report_signals` 뷰에서 신뢰도 factor에 **×1.3 보너스(1.0 상한)** 를 받아 survival_score comfort/risk 가중이 높아진다 — "실제로 가본 사람"의 제보를 더 신뢰해 허위제보 영향을 억제(CLAUDE.md §5·§7). `ReportCreateRequest`에 선택 `lat`/`lng`(둘 다 있을 때만 인증 시도), `ReportResponse`에 `verified`(프론트 "방문 인증" 배지용).
- 왜(why): ① **왜 100m·GPS인가** — 스마트폰 GPS 실측 오차(도심 수십m)를 감안해 "그 장소 근처에 실제로 있었다"를 판정하는 현실적 반경. 반경 검색과 동일한 `geography(geom)` 표기라 V3 GIST 함수 인덱스 경로를 그대로 탄다(전체스캔 없음, §0.4). ② **왜 뷰 가중을 곱셈(×1.3, 단조)으로** — 기존 신뢰도 공식(익명 0.7 / 로그인 0.7~1.0)에 verified면 1.3배(상한 1.0)를 곱하는 방식이라, **비검증 제보(기존 전체·좌표 미제공 포함)의 가중치는 완전 불변**이고 verified만 보너스를 받는다(회귀 없는 단조 개선). 익명 검증 0.91, 로그인 검증 최대 1.0. ③ **왜 EXIF는 뺐나** — 사진은 presign S3 URL이라 서버에 바이트가 없어 EXIF GPS를 못 읽는다(별도 다운로드는 비용·프라이버시 부담) → GPS 근접을 1차 신호로 하고 EXIF는 "선택" 후속으로 남김(ADR-0005 §④도 "(+선택 사진 EXIF)"로 표기).
- 검토한 대안: ① **좌표를 서버가 요청 IP로 추정** — 모바일 IP는 위치 정확도가 없어(기지국/통신사 게이트웨이) 무의미, 앱이 보낸 GPS가 유일한 신뢰 신호. ② **verified를 trust_score(유저 신뢰도)에 반영** — 유저 단위 가중은 별 메커니즘이라 스코프 확대. 이번은 "제보 단위 가중"(뷰)으로 한정해 한 메커니즘만 — 유저 trust 연동은 후속 여지. ③ **비검증 제보를 감점** — 기존 스코어를 흔들어 회귀 위험 → 대신 검증만 가산(단조).
- 검증: 컴파일 회귀 수정(Report.of·ReportResponse·ReportCreateRequest 시그니처 확장에 맞춰 호출부 5곳 갱신) 후 `ReportServiceTest`(4)·`ReportControllerTest`(11)·AI 테스트 green. IT `GpsVisitVerifyIT`(4: 100m 이내 verified=true·먼 곳 false·좌표없음 false·**verified 제보가 comfort 0.91 vs 비검증 0.7로 실제 가중 높음**)는 실 PostGIS라 CI 게이트. 기존 스코어 회귀 없음(비검증 불변) 확인.
- 산출물: `db/migration/V10__reports_verified_visit.sql`(컬럼 + 뷰 CREATE OR REPLACE) · `Report`(verified 필드·팩토리) · `dto/ReportCreateRequest`(lat/lng)·`dto/ReportResponse`(verified) · `PlaceRepository.isWithinMeters`(ST_DWithin 네이티브) · `ReportService.create`(verified 산정) · 테스트 1 IT + 호출부 4파일 갱신.
- 다음: 프론트(⑧)에서 제보 시 현재 위치 전송 + "방문 인증" 배지. 유저 trust_score 연동은 후속 여지.
- 관련: 브랜치 `feat/gps-visit-verify-p4`, ADR-0005 §④(구현)·ADR-0007(survival 뷰 — verified 가중 추가)·ADR-0001(geography 함수 인덱스), CLAUDE.md §5(제보 trust 가중)·§7·§0.4(인덱스 경로).

### 2026-07-10 — place_features.value 등급화 + 상세 노출 (브랜치 `feat/place-feature-grading-p4`, ADR-0005 §④)
- 한 일: 지금까지 인제스천이 적재만 하고(도서관 study_ok/quiet 등) 유저에게 전혀 안 보이던 `place_features`를 등급화해 상세 화면에 노출했다. `GET /places/{id}` 응답에 `features` 배열 추가 — 각 시설을 `FeatureGrade`(순수 함수)로 등급화: 콘센트 many/some/few, wifi fast/ok/slow, **noise_level(신설) quiet/moderate/loud**, 불리언 시설(air_conditioned·study_ok·quiet·seating·water·restroom·no_eyes) true/false. 각 등급은 표시 라벨(예 "콘센트 많음")·방향(POSITIVE/NEGATIVE/NEUTRAL)·present를 갖고, present=false(부재)는 상세에서 칩으로 안 그린다. 스키마 변경 0(value가 이미 VARCHAR — 등급은 값 문자열 규약).
- 왜(why): ① **왜 등급 규약을 코드에** — feature_type/value가 자유 문자열이라 "outlet=many"·"wifi=slow"·"noise_level=quiet" 같은 등급을 해석하는 단일 지점이 필요하다. 순수 함수(`FeatureGrade.of`)로 두면 DB 없이 단위테스트되고, 동의어(many/high/fast/3, few/low/slow/1)를 한 곳에서 흡수해 적재 소스가 달라도 일관 표시된다. ② **왜 상세 전용** — 시설 칩은 상세 화면 정보라 목록/마커(반경/bounds)에는 안 싣는다(aiSummary·weather와 같은 "상세가 더 많이 한다" 패턴, 목록 N+1·페이로드 방지). ③ **왜 present=false를 감추나** — "콘센트 없음"은 정보가 아니라 무표시가 자연(부재를 칩으로 나열하면 노이즈).
- 스코프 규율(중요): 핸드오프의 "→ comfort_score 정밀화"는 **표시 레이어 등급(polarity 방향 신호)** 로 전달하고, **survival_score 수치 재배선은 하지 않았다.** 이유: comfort는 현재 place_report_signals 뷰(제보 기반)+날씨로 조립되는데, 정적 시설 comfort를 수치로 넣으려면 스코어드 네이티브 쿼리 4개에 feature 집계를 조인해야 한다(간판 마커 점수를 바꾸는 SQL 레벨 변경 + N+1/조인 비용). 이는 "살·즉효"(ADR-0005 §④ 분류)가 아니라 신중한 간판 스코어링 패스라, 곁다리 시설이 간판 점수를 흔들지 않도록(CLAUDE.md §9) 분리해 후속으로 남겼다. polarity는 그 후속의 방향 신호 + UI 색 힌트로 미리 심어뒀다.
- 검증: 단위 `FeatureGradeTest`(7, 3단계·동의어·noise_level·불리언·미지타입 폴백·대소문자)·`PlaceSearchServiceTest`(12, 생성자에 PlaceFeatureRepository 추가 회귀) green. IT `PlaceFeatureDetailIT`(2, 상세에 등급 칩 노출·present=false 제외·빈 배열)는 실 PostGIS라 CI 게이트(place_features를 JdbcTemplate으로 심음 — 쓰기 엔티티 없음). `compileJava/compileTestJava` green.
- 산출물: `place/PlaceFeature`(읽기 엔티티)·`PlaceFeatureRepository`·`FeatureGrade`(등급 순수함수)·`dto/PlaceFeatureResponse` · `dto/PlaceResponse`(features 필드 + 상세 팩토리)·`PlaceSearchService`(features 조회·매핑) · 테스트 2 + PlaceSearchServiceTest 회귀.
- 다음: 프론트(⑧)에서 상세 시설 칩(등급별 아이콘/색, polarity 반영). comfort_score 수치 통합은 SQL 레벨 후속 패스.
- 관련: 브랜치 `feat/place-feature-grading-p4`, ADR-0005 §④·ADR-0006(features 백필 소스)·ADR-0009(comfort 조립 — 후속 통합 지점), CLAUDE.md §9(간판 vs 살 — 시설은 살, 마커 점수 불변)·§0.2(과설계 금지).

### 2026-07-10 — 추천 시나리오 focus/longstay 추가 (브랜치 `feat/recommend-focus-longstay-p4`, ADR-0008/ADR-0005 §④)
- 한 일: `/recommendations`에 시나리오 2개 추가 — **focus**(집중해서 공부·작업, `Weights(0.20,0.35,0.15,0.30)`, 후보 STUDY_CAFE/CAFE/LIBRARY/CIVIC)·**longstay**(오래 버틸 곳, `Weights(0.15,0.40,0.15,0.30)`, 후보 COOLING_SHELTER/LIBRARY/CIVIC/UNDERGROUND/CAFE/STUDY_CAFE). enum 값 + 가중치/카테고리만 추가 — 조립식·2단 검색·응답은 기존 `SurvivalScore.Weights` 오버로드를 100% 재사용(코드 로직 무변경).
- 왜(why): ADR-0005 §④의 "[살] 추천 시나리오 focus/longstay 추가(파라미터만)". 가중치 근거 — **focus**는 "조용히 오래 앉을 곳"이라 comfort↑(0.35) + risk 페널티 강화(0.30, 붐빔/소음/벌레 적극 강등), 거리는 후순위(0.20, 좋은 자리 위해 이동 감수). **longstay**는 폭염 장시간 체류라 comfort 압도(0.40) + risk 중시(0.30), 거리 최하(0.15, 자리 잡으면 오래 있음). 두 시나리오 후보에 CAFE/STUDY_CAFE를 넣은 이유: 카테고리 집합은 "의미"를 표현하는 것이라 데이터 유무와 독립 — 상권정보 승인 후 적재되면 자동 커버되고, 현재는 LIBRARY/CIVIC로 동작.
- 검토한 대안: focus/longstay를 같은 가중치로 둘까 했으나, longstay가 거리에 더 관대(0.15 vs 0.20)하고 comfort를 더 크게(0.40 vs 0.35) 봐서 "집중(자리 질)"과 "장기체류(눌러앉기)"의 뉘앙스를 가중치로 구분 — 단위테스트로 "longstay는 멀어도 쾌적한 곳이 가까운 밋밋한 곳을 이긴다"를 못 박음.
- 검증: `RecommendationScenarioTest`(8, +focus 혼잡 페널티·longstay comfort>distance·CSV STUDY_CAFE 포함·fromParam focus/longstay) green. compile green. 기존 시나리오 테스트 회귀 없음.
- 산출물: `RecommendationScenario`(FOCUS/LONGSTAY enum + import)·`RecommendationController`(설명/파라미터 갱신) · `RecommendationScenarioTest`(+2 케이스).
- 관련: 브랜치 `feat/recommend-focus-longstay-p4`, ADR-0008(시나리오 가중 랭킹)·ADR-0005 §④, CLAUDE.md §9.

### 2026-07-10 — 후기 커뮤니티: 댓글 + 리액션 (브랜치 `feat/review-community-p4`, CLAUDE.md §8 2차·살)
- 한 일: ERD(§8)에 설계돼 있던 `review_comments`·`reactions`를 백엔드로 구현했다(Flyway V11 + `domain.community` 신규 패키지). ① **후기 댓글**: `POST /reviews/{id}/comments`(로그인)·`GET /reviews/{id}/comments`(공개, 작성자 조인·오래된 순). ② **리액션("유용했어요")**: 다형 대상(REVIEW/REPORT/COMMENT) `POST /reactions`(멱등 추가)·`DELETE /reactions`(취소) — `uq_reaction`(target_type,target_id,user_id,type) 유니크로 중복 방지, 응답은 `{reacted, count}`. SecurityConfig에 두 write 경로 인증 요구 추가.
- 왜(why): ① **왜 지금·왜 최소 표면인가** — 사용자가 "전부 포함하되 커뮤니티는 '살'로만, 프론트 UI는 최소화"를 명시. CLAUDE.md §0-9가 "커뮤니티가 주인공이 되면 리뷰앱화 → 차별점 소멸"을 반복 경고하므로, 백엔드 API + ERD 테이블만 열고 survival_score(간판)에는 **어떤 것도 연결하지 않았다**(리액션 수가 스코어에 안 들어감). ② **왜 reactions는 FK 없는 다형인가** — 후기·제보·댓글 어디에나 붙는 범용 신호라 단일 FK로 못 묶는다. 대신 대상 존재를 애플리케이션이 target_type별 리포지토리(existsById)로 검증해 유령 대상 리액션을 막는다. ③ **왜 멱등/유니크인가** — "유용했어요"는 유저당 1표라 중복 추가는 no-op(유니크 + 사전 exists 체크 + 동시성 대비 DataIntegrityViolation catch)이어야 카운트가 정확하다.
- 검토한 대안: ① **댓글에 대댓글(스레드)·수정/삭제** — 2차 살의 과확장이라 제외(§0-2, 필요 입증 후). ② **리액션 종류 다양화(👍😍 등)** — 지금은 HELPFUL 하나(EnumType.STRING이라 값 추가만으로 확장). ③ **POST 토글(한 엔드포인트로 추가·취소)** — REST 의미가 흐려져 POST(추가)/DELETE(취소)로 분리(멱등·명시적).
- 스코프 규율: 프론트 UI는 이번에 만들지 않는다(⑧에서 최소만 — 커뮤니티가 전면에 나오지 않게). survival_score·place_report_signals·SurvivalScore는 한 줄도 안 건드림(§0-9).
- 검증: IT `CommunityFlowIT`(4: 댓글 작성→목록·비로그인 401·리액션 멱등(2회 눌러도 count=1)·취소 count=0·없는 대상 404)는 실 PostGIS+Security라 CI 게이트. `compileJava/compileTestJava` green.
- 산출물: `db/migration/V11__review_comments_reactions.sql` · `domain/community/`(ReviewComment·Reaction 엔티티, ReactionTarget/Type enum, 2 리포지토리, 2 서비스, CommunityController, 4 DTO, 1 뷰) · `SecurityConfig`(인증 경로 +3) · 테스트 1 IT.
- 다음: 프론트(⑧)에서 상세의 후기에 댓글·"유용했어요" 최소 UI(간판을 가리지 않게).
- 관련: 브랜치 `feat/review-community-p4`, CLAUDE.md §8(ERD)·§0-9(커뮤니티=살, 리뷰앱화 경고)·§0-2(과설계 금지), ADR-0007(survival 분리 — 커뮤니티 무영향).

### 2026-07-10 — 모더레이션 확장: 신고 RESOLVED 시 콘텐츠 숨김 + 상태별 이력 (브랜치 `feat/moderation-expand-p4`, V12)
- 한 일: 기존 신고 큐(#33 flags)는 상태만 마킹하고 실제 콘텐츠는 그대로 남아 "이빨이 없던" 것을 실효화했다. **신고를 RESOLVED(타당)로 처리하면 대상(제보/후기)이 hidden 처리**돼 공개에서 사라진다(Flyway **V12** `reports.hidden`/`reviews.hidden`). hidden 제보/후기는 **공개 조회·survival_score(뷰)·급증 알림·시간대별 혼잡·AI 요약·후기 목록에서 전부 제외**된다. DISMISSED(오신고)면 콘텐츠는 그대로. 추가로 ADMIN 상태별 이력 조회 `GET /admin/flags?status=PENDING|RESOLVED|DISMISSED`(처리 이력 확인).
- 왜(why): ① **왜 hidden(soft)인가** — 물리 삭제 대신 숨김이면 오처리 복구 여지가 있고, 이력·감사 추적이 남는다(모더레이션 표준). ② **왜 전(全) 공개 경로에서 제외인가** — 숨김이 "공개 조회"에서만 빠지고 스코어/급증엔 남으면 반쪽짜리다. 확정된 허위 제보가 survival_score를 흔들거나 급증 알림을 유발하면 안 되므로, 6개 읽기 경로(recent·view·surge×3·popular-times·AI·review list) 전부에 `NOT hidden`을 걸었다. ③ **왜 RESOLVED에서만 숨기나** — RESOLVED="신고가 타당함"이 곧 "콘텐츠 부적절"이라 숨김의 트리거로 자연스럽다. 숨김은 멱등(`hide()`)이라 같은 대상에 여러 신고가 각각 RESOLVED돼도 안전.
- 안전성(회귀 없음): hidden 기본 false → 기존 제보/후기 전부 노출 유지, place_report_signals 뷰도 V10과 동일 결과(숨김이 없으므로). 뷰는 CREATE OR REPLACE로 verified 가중(V10) 보존 + `AND NOT r.hidden` 한 줄만 추가.
- 검토한 대안: ① **물리 삭제(DELETE)** — 복구 불가·감사 추적 소실이라 기각(soft-hide가 모더레이션 관행). ② **숨김을 공개 조회에서만** — 스코어/급증에 남아 반쪽. 전 경로 제외가 정합. ③ **자동 임계 숨김(N개 신고 시 자동)** — 오남용(신고 폭탄) 위험이라 사람(ADMIN) 판단을 유지, 자동화는 후속.
- 검증: IT `ModerationHideIT`(2: RESOLVED→공개 조회에서 사라짐 + status=RESOLVED 이력에 잡힘 / DISMISSED→유지)는 실 PostGIS+Security라 CI 게이트. 메서드 리네임(`findTop20...AndHiddenFalse...`) 호출부(AiSummaryService + 테스트 2) 갱신 후 단위 회귀 green. compile green.
- 산출물: `db/migration/V12__moderation_hidden.sql`(컬럼2+뷰) · `Report`/`Review`(hidden·hide()) · `ReportRepository`(recent/congestion NOT hidden)·`ReportSurgeRepository`(3쿼리 NOT hidden)·`ReviewRepository`(list NOT hidden)·`AiSummaryService`(hidden 제외) · `FlagService`(resolve→hideTarget, byStatus)·`AdminFlagController`(GET /admin/flags?status=) · 테스트 1 IT.
- 다음: 프론트(⑧) ADMIN 큐에 처리 이력 탭(선택). 자동 임계 숨김·복구(unhide) 경로는 후속.
- 관련: 브랜치 `feat/moderation-expand-p4`, #33(flags 기반)·ADR-0007(뷰)·ADR-0016(급증)·ADR-0005 §④(혼잡/verified), CLAUDE.md §0-7(모더레이션 처음부터)·§9(관리자 큐)·§6(표현).

### 2026-07-10 — 프론트: 백엔드 신규 기능 노출(카테고리 필터·시설 등급·방문 인증·시나리오·AI요약) (브랜치 `feat/frontend-surface-p4`)
- 한 일: 이번 세션에 백엔드가 새로 노출한 것들을 프론트에 반영했다(BFF가 순수 패스스루라 타입+렌더만 갱신). ① **CAFE/STUDY_CAFE 카테고리** — Category 타입·CATEGORY_META·FILTER_CATEGORIES에 추가(데이터 승인 전이라 결과는 비지만 필터 UI 선반영, 핸드오프 지시). ② **place_features 등급 칩(④)** — `PlaceFeature`(type/value/level/label/polarity) 타입 신설, FeaturePills를 등급 라벨("콘센트 많음")+polarity 색(POSITIVE 민트/NEGATIVE 레드/NEUTRAL 회색)으로 재작성. ③ **방문 인증 배지(③)** — Report에 `verified` 추가, 상세 최근제보에 "방문 인증" 배지, 제보 제출 시 실측 GPS(!isFallback)면 lat/lng 전송해 verified 실동작. ④ **추천 시나리오 focus/longstay(⑤)** — Scenario 타입·SCENARIO_META·ScenarioButtons ORDER에 추가(급해요 탭에 2개 버튼). ⑤ **AI 한줄 요약 라이브** — 상세의 "P3 준비중" 플레이스홀더를 실제 `place.aiSummary`로 교체(있으면 요약 카드, 없으면 안내 폴백).
- 왜(why): ① **왜 타입+렌더만인가** — BFF(app/api/*)가 ALB 응답을 그대로 프록시(패스스루)라 verified/features/aiSummary가 자동으로 흘러온다 → 프론트는 TS 타입과 표시 컴포넌트만 고치면 된다(백엔드 계약 재-매핑 없음). ② **왜 실측 GPS일 때만 좌표 전송** — geo 컨텍스트는 권한 거부 시 폴백 센터(동작구)를 반환하는데, 그걸 "제보자 위치"로 보내면 폴백 근처 장소에 허위 verified가 붙는다 → `!isFallback`으로 실측만 전송(정확성). ③ **커뮤니티 UI 최소** — 사용자 지시대로 후기 댓글/리액션(⑥) UI는 이번에 안 만들었다(커뮤니티가 전면에 나오지 않게, 간판 우선).
- 스코프 규율: 급증 SSE 구독·popular-times 히트맵은 이번 스코프 밖(핸드오프 ⑧은 "필터·등급 UI·UX 폴리시"로 한정) — 백엔드는 준비됐으니 후속에 additive.
- 검증: `npm run typecheck`·`lint`·`build` 전부 green(프론트 CI와 동일 게이트). 20개 라우트 정상 빌드.
- 산출물: `types/place.ts`(Category+2·PlaceFeature·verified·aiSummary·Scenario+2·payload lat/lng) · `lib/categories.ts`(CATEGORY/FEATURE/SCENARIO_META·FILTER +CAFE/STUDY_CAFE·focus/longstay) · `components/place/FeaturePills.tsx`(등급 렌더 재작성) · `components/place/PlaceDetailOverlay.tsx`(verified 배지·라이브 AI요약) · `components/urgent/ScenarioButtons.tsx`(ORDER) · `app/(shell)/report/page.tsx`(GPS 좌표 전송).
- 다음: (후속) 급증 배지·EventSource 구독·popular-times 히트맵·커뮤니티 최소 UI. 카테고리 필터는 상권정보 승인 시 결과가 채워진다.
- 관련: 브랜치 `feat/frontend-surface-p4`, ADR-0004(BFF 패스스루)·ADR-0005 §④(features/verified/시나리오)·ADR-0010(AI요약)·ADR-0016(급증, 후속), CLAUDE.md §9(간판 우선, 커뮤니티는 살).

### 2026-07-10 — 커버리지 ratchet(0.60→0.70) + popular-times Redis 캐시 (브랜치 `chore/coverage-cache-p4`)
- 한 일(⑨, 품질·인프라): ① **JaCoCo LINE floor 0.60→0.70** — ①~⑧에서 서비스·순수함수·DTO 테스트가 대폭 늘어 로컬(IT skip) 실측이 65.7%→**71.0%**로 올랐다. "로컬이 하한, CI는 그 이상" 원칙대로 측정치 바로 아래인 0.70으로 잠갔다(회귀 방지). ② **시간대별 혼잡(popular-times, ②) Redis TTL 캐시** — `ReportService.popularTimes`에 `@Cacheable("popularTimes", key=#placeId)`, TTL 1시간. `RedisCacheConfig`에 `POPULAR_TIMES_CACHE` + `List<PopularTimesSlot>`를 TypeFactory로 원소 타입까지 바인드한 직렬화기를 등록.
- 왜(why): ① **왜 popular-times를 캐시하나** — "과거 이력의 요일×시간 분포"라 느리게 변하는데(새 제보 하나가 슬롯을 거의 안 움직임) 상세 조회마다 group-by를 돌리는 건 낭비다. 반면 survival_score·급증(①)은 "지금"을 봐야 해 캐시하면 안 된다 — 그래서 캐시 대상을 slow-moving 파생만 골랐다(캐시 전략의 핵심은 "무엇을 캐시하지 않을지"). TTL 1h면 새 제보/모더레이션 숨김(⑦)의 반영이 최대 1h 지연되지만, 히스토리 히트맵엔 허용 범위. ② **왜 타입 바인드 직렬화(TS-011/012 교훈)** — 제네릭 `List<PopularTimesSlot>`를 GenericJackson(무타이핑)으로 캐시하면 GET 시 LinkedHashMap 리스트로 복원돼 캐스트 500이 난다(TS-011/012 재발). `TypeFactory.constructCollectionType(List, PopularTimesSlot)`로 원소 타입까지 못 박고, `RedisCacheConfigTest` 왕복 테스트로 Redis 없이 회귀를 막았다. ③ **왜 CacheErrorHandler 그대로** — Redis 장애 시 캐시 계층을 우회해 원본 쿼리로 폴백(가용성을 캐시에 의존시키지 않음, ADR-0009 원칙 유지).
- 검토한 대안: ① **급증(①)/survival 캐시** — 실시간성이 생명이라 캐시하면 목적이 깨져 제외. ② **wrapper record로 캐시** — 단일 concrete 타입이라 안전하지만 API/테스트 셰이프가 바뀌어, TypeFactory 직렬화기(왕복 테스트로 검증됨)로 List를 그대로 캐시하는 편이 덜 침습적. ③ **floor를 0.71로** — 측정치와 너무 붙어 사소한 변동에 깨지므로 0.70으로 여유.
- 검증: `RedisCacheConfigTest`(2, weather + **List<PopularTimesSlot> 왕복**)·`PopularTimesCacheProxyTest`(1, 2회 호출 시 집계 쿼리 1회=캐시 히트) green. `jacocoTestCoverageVerification` floor 0.70 로컬 통과(실측 71.0%). compile green.
- 산출물: `build.gradle`(floor 0.70+주석) · `RedisCacheConfig`(POPULAR_TIMES_CACHE·타입 바인드 직렬화기·등록) · `ReportService.popularTimes`(@Cacheable) · 테스트 `RedisCacheConfigTest`(+1)·`PopularTimesCacheProxyTest`(신규).
- 관련: 브랜치 `chore/coverage-cache-p4`, TS-011/012(캐시 직렬화 함정)·ADR-0009(캐시=선택 계층, CacheErrorHandler)·ADR-0005 §④(popular-times)·CLAUDE.md §7(캐시 전략)·§0-2(무엇을 캐시 안 할지 = 과설계 방지).

### 2026-07-10 — 상권정보(카페/스터디카페) 계약 검증 + 코드기반 분류 + 격자 적재 (브랜치 `feat/store-ingest-cafe-studycafe`)
- 배경: 사용자가 "data.go.kr 활용신청 모두 승인됨"을 알려, 그간 외부 승인 대기로 막혀 있던 **상권정보 오픈API(B553077 상가업소정보)** 를 실호출로 재확인했더니 `resultCode=00 NORMAL SERVICE`(2026-07-09까지 403 미승인 → 승인 확인). ADR-0006에 "승인 후 계약 재검증 필요"로 남겨둔 미검증 스캐폴드(`domain.ingest.storeapi`)를 **실측으로 검증·정정하고 실적재 경로까지 완성**했다.
- 한 일: ① **응답 계약 정정(TS-026)** — 스캐폴드는 도서관 표준 API를 본떠 `{response:{header,body}}` 래퍼 + 문자열 좌표를 가정했으나, sdsc2 실응답은 **`{header,body}` 최상위(래퍼 없음)** + **lon/lat·totalCount가 JSON 숫자**였다 → `StoreApiResponse`(래퍼 제거)·`StoreRecord`(lon/lat `Double`) 정정. ② **업종코드 확정 분류** — 승인 전엔 소분류코드를 몰라 분류명 텍스트 매칭이 임시였는데(ADR-0006 착수순서 ①=미착수), 실호출로 **카페=`I21201`, 독서실/스터디카페=`R10202`** 확정 → `StoreCategoryMapper`를 코드맵 authority로 승격(`targetCodes()`/`classifyByCode`), 분류명 매칭은 방어적 폴백으로 강등. ③ **서버측 업종필터로 효율화** — 광화문 1.5km에 상가 6,831건 중 카페 수백뿐이라, 전체를 받아 클라에서 거르던 것을 `indsSclsCd` 서버측 필터로 **대상 업종만 페이지네이션**(API 호출량 한 자릿수 배 절감). ④ **격자 커버리지** — `ingestArea(bbox, radius)`가 반경 원들로 bbox를 격자 순회(원이 격자칸 내접 커버, dedup은 멱등 upsert). 러너 `--ingest.source=stores --ingest.bbox= --ingest.radius=`(레거시 cafe/study_cafe 별칭 유지) + `infra/scripts/prod-ingest-stores.sh`.
- 왜(why): ① **왜 코드 기반인가** — 분류명은 개편·표기흔들림(예: "독서실/스터디 카페"에 공백)에 취약하고, 무엇보다 서버측 필터를 코드로 걸어야 호출량이 준다. 소분류코드는 실측으로 안정 확정됐다. ② **왜 서버측 필터인가(트렌드·근거)** — data.go.kr sdsc2는 `indsSclsCd` 필터를 공식 지원(실측 동작). 클라 필터는 카페 1건 얻자고 상가 100건을 받아 버리는 낭비 → 대용량·주기동기화(P3)에서 rate-limit·비용 직결이라 서버측이 정석. ③ **왜 격자인가** — 행정동코드 목록(별도 데이터셋) 없이 우리 PostGIS 반경검색과 동일한 정신모델(lat/lng/radius)로 전국을 덮을 수 있다(ADR-0006 반경 선택 근거). 겹침은 멱등 upsert가 흡수. ④ **왜 좌표 지오코딩이 거의 불필요한가** — 카페/스터디카페는 WGS84 좌표를 직접 제공(실측) → 화장실 6만 건처럼 카카오 지오코딩 물량이 안 터진다(ADR-0006 상권정보 채택 근거 재확인).
- 스코프 규율(§0-2, §0-9): 카테고리는 CAFE/STUDY_CAFE 2개만(ADR-0006 확정, 신규 없음). STUDY_CAFE만 `study_ok/quiet` 낮은 confidence 백필, CAFE는 UGC 전용(공공데이터에 '공부가능' 없음). survival_score·마커 수치엔 손 안 댐(간판 불흔들). soft-delete는 부분 스냅샷이라 의도적 미지원(P3 무인화에서 diff).
- 검증: 실 API 프로브로 계약 4종 실측 확정(래퍼 없음·숫자 좌표·I21201/R10202·서버필터 동작). 단위 14건 green(`SmallBusinessStoreApiClientTest` 실응답 픽스처 재작성 4 · `StoreCategoryMapperTest` 코드분류 5 · `StoreIngestionServiceTest` 코드별 분리·격자집계 5). 전체 `./gradlew test` green. CI Backend(Gradle) pass 확인 후 머지(PR #56, TS-025 규율).
- **프로덕션 실적재(2026-07-10, 배포 rev49):** `prod-ingest-stores.sh`로 ECS one-off task 2회. ① **동작구 스모크**(bbox 126.90,37.48,126.99,37.52, 15셀): upserted 5,359(CAFE 4,602·STUDY_CAFE 757), geocoded 0. ② **서울 전역**(bbox 126.76,37.42,127.19,37.71, 340셀, ~18분): 누적 upsert 57,703(격자 겹침 포함). **bounds 타일 스윕으로 distinct 실측: CAFE 26,726 · STUDY_CAFE 3,160 = 29,886곳**(0 캡). geocoded 0(전량 WGS84 내장). 실측: `/places?category=CAFE|STUDY_CAFE` 서울 전역 200(강남 빽다방·종로 스터디독서실·노량진 플랜에이스터디카페), STUDY_CAFE 상세 `features=[quiet,study_ok]` 백필 확인, survival=UNKNOWN(제보 없는 신규 장소 — 간판 스코어/마커 불흔들, §0-9 정합). `⚠️ 교훈`: `aws ecs wait tasks-stopped`는 100회×6s=10분 캡이라 대형 격자(18분)는 조기 타임아웃 → 스크립트 exit는 태스크 완료가 아니라 **CloudWatch `[store-api] ingestArea 완료` 로그 + 태스크 exitCode로 판정**.
- 산출물: `storeapi/StoreApiResponse`·`StoreRecord`·`StoreApiClient`·`SmallBusinessStoreApiClient`·`StoreCategoryMapper`·`StoreIngestionService`(전면 정정) · `IngestionRunner`(stores/bbox 모드) · `infra/scripts/prod-ingest-stores.sh`(신규) · 테스트 3파일 재작성 · ADR-0006(구현 정정 갱신) · TROUBLESHOOTING TS-026.
- 남은 외부 블로커(변화): 상권정보 = **해소(승인 확인)**. 무더위쉼터 전국 = **여전히 블로킹** — data.go.kr엔 서비스 없음(표준데이터가 재난안전데이터공유플랫폼 safetydata.go.kr로 이관, DSSP-IF-10942), safetydata는 **별도 포털·별도 서비스키**라 datago 키로 `resultCode 30`(미등록). 사용자가 safetydata.go.kr 가입+활용신청+전용키 확보해야 진행 가능(HANDOFF ⏳).
- 관련: 브랜치 `feat/store-ingest-cafe-studycafe`, ADR-0006(공부공간 커버리지)·ADR-0002(멱등)·ADR-0003(지오코딩 폴백), TS-026, CLAUDE.md §3(전국 적재·커버리지)·§0-3(멱등 인제스천)·§0-4(GiST 반경)·§9(간판 vs 살).

### 2026-07-10 — 무더위쉼터 전국 적재(safetydata.go.kr) — 마지막 외부 블로커 해소 (브랜치 `feat/shelter-safetydata-ingest`)
- 배경: 사용자가 이미 신청해둔 **safetydata.go.kr 전용 서비스키**(무더위쉼터 `DSSP-IF-10942`, 만료 2027-07-10, 일일 1000호출)를 공유. safetydata는 data.go.kr과 **완전히 별개 포털·별개 키**라 datago 키로는 `resultCode 30`(미등록)이던 마지막 블로커였다. 키 확보로 해소.
- 한 일: safetydata 무더위쉼터 오픈API를 **새 소스(도서관 JSON 오픈API 패턴 재사용)**로 붙였다. ① 신규 패키지 `domain.ingest.safetydata`(`SafetyDataShelterClient`·`SafetyDataApiClient`·`ShelterApiResponse`·`ShelterPage`·`ShelterRecord`·`ShelterIngestionService`). ② 러너 `--ingest.source=shelter --ingest.deactivate-stale=true`. ③ application.yml `safetydata.service-key: ${SAFETYDATA_SERVICE_KEY:}`. ④ `infra/scripts/prod-ingest-shelter.sh`(키를 RunTask env 오버라이드로만 주입 — 규칙 D).
- 계약 검증(TS-027, 코드 前 실호출): safetydata V2는 data.go.kr 표준과 **응답 봉투가 다르다** — `response` 래퍼 없이 `header`·`totalCount`·`pageNo`·`numOfRows`·`body`가 **최상위**, `body`는 `items` 없이 **레코드 배열 그 자체**. JSON 키는 대문자 스네이크(`RSTR_NM`·`LA`·`LO`·`RSTR_FCLTY_NO`·`COLR_HOLD_ARCNDTN`)라 `@JsonProperty` 매핑. 좌표 LA/LO는 **WGS84 숫자**(실측 결측 0/1000). 전국 `totalCount=60,297`.
- 왜(why): ① **왜 별도 소스·별도 서비스인가** — safetydata는 포털/키/응답봉투가 data.go.kr과 달라 SourceSpec(CSV) 경로에 못 얹는다. 도서관 오픈API 서비스(`PublicLibraryIngestionService`)와 동형(파일 없음·좌표 내장·페이지네이션 전량 수집)이라 그 패턴을 복제하고 업서트·지오코딩·soft-delete 컴포넌트만 재사용(DRY, ADR-0002/0003). ② **왜 source=cooling_shelter_std 재사용 + deactivate-stale인가** — 기존 100건 CSV 샘플과 같은 source 값을 써서, 전국 스냅샷에 없는 샘플 external_id를 soft-delete로 걷어내 쉼터 레이어를 실데이터(6만)로 수렴시킨다. ③ **왜 deactivate-stale을 complete일 때만인가** — 6만건 중간에 페이지 오류로 끊긴 부분 스냅샷으로 deactivate하면 멀쩡한 쉼터를 지우는 사고 → 첫 페이지 totalCount 대비 수집량으로 게이트(부분 수집이면 건너뛰고 warn). ④ **왜 air_conditioned 조건부 백필인가** — 냉방기 보유수(COLR_HOLD_ARCNDTN)>0이면 낮은 confidence의 air_conditioned를 심는다(도서관 seatCo>0→study_ok와 동형). "시원함"은 무더위쉼터의 정의적 속성이라 comfort/냉방 필터가 콜드스타트에서 바로 동작(간판 수치는 UGC 확정, §9). ⑤ **왜 키를 RunTask env 오버라이드로만인가** — 쉼터 적재는 one-off라 런타임 서빙엔 키가 불필요 → SSM/태스크데프에 상시 배선하지 않고 실행 시에만 주입(공격면·규칙 D 최소).
- 검증: 실호출로 계약 확정(봉투·숫자좌표·6만건·필드명). 단위 7건 green(`SafetyDataShelterClientTest` 실응답 픽스처 3 · `ShelterIngestionServiceTest` 소스·air백필·complete게이트·지오코딩 4). 전체 `./gradlew test` green.
- **프로덕션 실적재(2026-07-10, 배포 rev50) + IP 제한 발견·우회(TS-027 §후속):** 배포 후 ECS one-off로 `--ingest.source=shelter`를 돌리자 **`resultCode=32 UNREGISTERED IP ERROR`** — safetydata 키가 **발급 시점의 등록 IP(사용자 로컬)에 잠겨** 있어 AWS Fargate egress IP에서 거부됐다(내 로컬 curl은 등록 IP라 동작). `complete` 게이트가 fetched=0을 감지해 **deactivate-stale을 건너뛰어 기존 샘플 보존**(데이터 손실 0 — 방어 설계 적중). RDS가 프라이빗이라 적재는 반드시 VPC 내(ECS)인데 Fargate IP는 유동·NAT는 비용상 배제 → 직접 API 경로가 이 키로는 ECS에서 불가. **우회(원래 의도된 CSV 스냅샷 경로)**: 등록 IP(로컬 세션)에서 전량 60,297건 다운로드 → 헤더가 `SourceSpec.COOLING_SHELTER` 별칭과 일치하는 CSV(RSTR_FCLTY_NO/RSTR_NM/RN_DTL_ADRES/LA/LO, per-row 주소 폴백 해석) 생성 → GitHub Release `data-v1/shelters.csv`(6.9MB) 업로드 → ECS가 **기존 CSV 파이프라인**(`--ingest.source=cooling_shelter --ingest.url= --ingest.deactivate-stale=true`)으로 적재. 결과: `source=cooling_shelter_std total=60,297 upserted=60,297 skipped=0 geocoded=0 deactivated=0`(기존 100 샘플이 60k의 부분집합이라 삭제 없이 갱신 — 중복 0). 실측: 서울 178·부산 196·대전 181·광주 203·제주 107(반경3km), 실제 쉼터명(휴서울이동노동자쉼터 등). **주의: CSV 파이프라인은 air_conditioned 조건부 백필을 안 한다**(그건 API 서비스 전용) — 후속. API 서비스(`ShelterIngestionService`)는 **등록 IP/로컬·향후 IP 해제 시의 경로**로 유지(계약 검증·테스트 완료).
- 산출물: `domain/ingest/safetydata/`(6클래스) · `IngestionRunner`(shelter 소스) · `application.yml`(safetydata.service-key) · `infra/scripts/prod-ingest-shelter.sh` · 테스트 2파일 · TS-027. 비밀은 `.local/safetydata.env`(gitignore)에만.
- 결과: **남은 외부 승인 블로커 = 0건.** 로드맵 P1~P5 데이터 커버리지(쉼터·화장실·도서관·카페·스터디카페) 전부 실데이터로 라이브.
- 관련: 브랜치 `feat/shelter-safetydata-ingest`, TS-027, ADR-0002(멱등)·ADR-0003(지오코딩 폴백), CLAUDE.md §3(전국 표준데이터 적재)·§4(무더위쉼터=기본 레이어)·§6(공포 조장 금지 표현)·§9(간판 vs 살).

## 2026-07-10 — A1. 시설(place_features) → survival_score comfort 통합 (브랜치 `feat/a1-comfort-feature-sql`, ADR-0017)
- 배경(docs/BACKLOG.md A1): `place_features`(에어컨·콘센트·wifi·좌석·study_ok 등)는 상세 등급 칩(FeatureGrade, ADR-0005 §④)으로 **표시만** 되고 survival_score에는 안 들어가, 냉방 쉼터·콘센트 카페가 **무제보이면 comfort=0**으로 취급됐다. 정적 시설을 스코어에 반영해 간판(종합점수·추천 랭킹)을 정밀화하되, 저신뢰 PUBLIC feature가 UGC를 덮거나 마커를 흔들지 않게(§9) 한다.
- 한 일: ① **신규 뷰 V13 `place_feature_signals`** — 장소별 `feature_comfort[0,1]`를 polarity·confidence 가중으로 집계(긍정 불리언 시설 conf×0.5, 콘센트/wifi 등급별 강/중/약, noise=loud 차감, 성분당 상한 0.5로 단일 시설 포화 방지). `place_report_signals`(제보)와 **별도 뷰**(그 뷰는 제보 있는 장소만 GROUP BY라 무제보 시설 장소가 행이 없음) → 4개 스코어드 쿼리가 **둘 다 LEFT JOIN**. ② `ScoredPlaceView.getFeatureComfortScore()` + `PlaceRepository` 4쿼리에 컬럼·조인 추가. ③ **`SurvivalScore` 단조 상승 조립** — `effectiveComfort`가 제보·날씨 base(ADR-0009 불변) 위에 `base+(1−base)·feature·GAIN(0.5)`로 시설을 **올리기만** 한다. ④ `PlaceResponse`·`RecommendationService`가 featureComfort 전달. ⑤ 등급(마커 3색)은 **여전히 reportCount로만** 판정(무제보→UNKNOWN 유지).
- 왜(why): ① **왜 별도 SQL 뷰인가** — 반경/bounds는 수백 장소 배치라 Java 로딩은 N+1/대형조인, 간판(DB 시공간 집계, ADR-0007) 철학과 어긋남. 제보/시설을 각각 뷰로 두고 LEFT JOIN하면 DRY·인덱스 경로 유지. FULL OUTER JOIN 재구성 회피. ② **왜 단조 상승(올리기만)인가** — verified(V10)와 같은 무회귀 철학. 내리지 않으니 시설 추가가 등급을 안 떨어뜨려 §9(흔들림 최소). 체감(감소 수익)이라 UGC 강한 장소는 거의 안 움직여 "PUBLIC이 UGC를 못 덮게"(A1 함정) 충족. ③ **왜 등급은 reportCount 게이팅 유지인가** — 등급=" **지금** 상태를 아는가"(live). 에어컨은 정적 사실이지 "지금 시원" 신호가 아님. 무제보 냉방쉼터를 초록으로 칠하면 거짓 함의 + 14만 장소 일제 색변화로 §9 위반. **제보 있는 장소에선** 시설이 OKAY→GOOD 승격 가능(수용 기준 "comfort↑로 등급 상승"을 이 경로로 충족). 추천 랭킹은 등급과 무관히 feature_comfort 반영 → 무제보 냉방쉼터도 rest30에서 상승(간판 정밀화 실효). ④ **트렌드 근거** — "정적 속성=단조 부스트, 실시간 신호=등급 게이트" 분리는 검색·추천 랭킹의 표준(정적 feature는 가산점, freshness/live는 별도 게이트). ADR-0007/0008/0009 계보에 additive.
- 스코프 규율: 폴백 회귀 0 — featureComfort=null(시설 없는 장소)이면 `effectiveComfort`가 정확히 기존 경로라 WeatherComfortIT·기존 단위테스트 전부 불변. 뷰가 FeatureGrade(표시)와 truthy/polarity를 의도적 이중 표현(관심사 분리), 실 PostGIS IT로 확증.
- 검증: 단위 green — `SurvivalScoreTest`에 시설 조립 5건 추가(폴백 회귀·단조 상승 0.75·무회귀·무제보 UNKNOWN 유지·OKAY→GOOD 승격) + `PlaceSearchServiceTest`·`RecommendationServiceTest` 스텁에 getter 추가. IT — `SurvivalScoreIT`에 "시설이 comfort로 흘러 무제보 comfort>0·등급 UNKNOWN" 1건(JdbcTemplate로 place_features 직접 심음). 로컬 IT는 colima skip(TS-009) → **CI Backend(Gradle) pass 확인 후 머지**(TS-025).
- 산출물: `V13__place_feature_signals_view.sql`(신규) · `ScoredPlaceView`·`PlaceRepository`·`SurvivalScore`·`PlaceResponse`·`RecommendationService` · 테스트 3파일 · ADR-0017.
- 관련: ADR-0017, ADR-0007(SQL 집계+Java 조립)·ADR-0009(날씨 additive)·ADR-0005 §④(FeatureGrade), CLAUDE.md §5·§9, docs/BACKLOG.md A1. A3(쉼터 냉방 백필)이 붙으면 냉방쉼터 comfort↑ 실동작.

## 2026-07-10 — A3. 쉼터 air_conditioned 조건부 백필 (코드 enablement) (브랜치 `feat/a3-shelter-aircon`)
- 배경(docs/BACKLOG.md A3): 무더위쉼터는 IP 제한 우회로 **CSV 스냅샷 경로**로 적재돼(WORKLOG 2026-07-10 쉼터 항목), 냉방기 보유수(COLR_HOLD_ARCNDTN) 기반 air_conditioned 백필이 안 됐다(그 로직은 IP 잠긴 API 서비스 `ShelterIngestionService` 전용). A1(시설 comfort SQL 통합)이 붙었으니, 냉방쉼터에 air_conditioned가 있어야 comfort↑가 실동작한다. **CSV 파이프라인에도 조건부 백필을 지원**하도록 코드를 확장한다.
- 한 일(코드): ① `SourceSpec`에 nullable `ConditionalFeature(columnAliases, FeatureSpec)` 추가 — COOLING_SHELTER는 `COLR_HOLD_ARCNDTN` 등 별칭 + `air_conditioned/true/conf0.4`, 화장실은 null. ② `StandardCsvParser`가 조건 컬럼을 읽어 값>0인 external_id를 `Result.conditionalFeatureIds`로 수집(컬럼 없으면 빈 셋 → 하위호환 no-op). ③ `IngestionService`가 그 id들에 `backfillFeatures`(기존 멱등 백필 메서드 재사용, ON CONFLICT DO NOTHING).
- 왜(why): ① **왜 CSV 파이프라인 확장인가** — 쉼터가 IP 우회로 CSV 경로로만 적재되는 현실에서, 그 경로에 조건부 백필이 없으면 냉방 신호를 영영 못 넣는다. API 서비스는 IP 잠금이라 prod 불가(WORKLOG 2026-07-10). ② **왜 레코드별 조건부인가(카테고리 균일 X)** — 모든 쉼터가 냉방인 게 아니라 `COLR_HOLD_ARCNDTN>0`인 것만 → 도서관 `seatCo>0→study_ok`와 동형의 레코드 조건. `DefaultFeatureBackfill`(카테고리 균일)로는 표현 불가라 파서가 컬럼을 읽는다. ③ **왜 confidence 0.4(낮음)인가** — PUBLIC 백필이라 UGC(제보·후기)가 냉방 여부를 덮으면 그쪽 우선(ON CONFLICT DO NOTHING). A1 뷰가 confidence로 가중하므로 낮은 값이 "정적 사실은 약하게, UGC는 강하게"를 보장. ④ **왜 하위호환 no-op인가** — 현재 라이브 shelters.csv(별칭 헤더, 냉방 컬럼 없음)로 재적재해도 `conditionalFeatureIds`가 비어 아무 백필도 안 일어나 안전.
- 스코프 규율: 순수 코드 enablement — 프로덕션 데이터는 **아직 안 바뀐다**. 실제 air_conditioned 적재는 **냉방 컬럼을 포함한 shelters.csv 재생성 + 재적재**가 필요하고, 그건 safetydata 키(등록 IP 로컬 다운로드)로 CSV를 다시 뽑는 **대량 재적재(사용자 입력 지점, BACKLOG 명시)**다. 코드는 그 재적재가 한 커맨드로 동작하도록 준비 완료.
- 검증: 단위 green — `StandardCsvParserTest` 2건(냉방기>0만 수집·컬럼 없으면 빈 셋 하위호환). IT — `IngestionIdempotencyIT`에 냉방기>0 쉼터에만 air_conditioned 백필되는 end-to-end 1건(실 PostGIS, JdbcTemplate 검증). 로컬 IT colima skip(TS-009) → CI Backend(Gradle) pass 확인 후 머지(TS-025).
- 남은 데이터 작업(사용자 입력 지점): 냉방 컬럼 포함 shelters.csv 재생성(등록 IP 로컬 + safetydata 키) → Release 업로드 → `prod-ingest.sh cooling_shelter <url> UTF-8`. 세션 마지막에 배치로 제안·수행.
- 산출물: `SourceSpec`·`StandardCsvParser`·`IngestionService`(각 소폭) · 테스트 2파일. 마이그레이션 없음(place_features는 V2, 백필은 기존 경로).
- 관련: docs/BACKLOG.md A3, ADR-0006(백필 규약)·ADR-0017(A1 comfort 통합 — 냉방쉼터 comfort↑ 실동작 연계), CLAUDE.md §4(무더위쉼터=기본 레이어)·§0-3(멱등).

## 2026-07-10 — A2. verified 방문인증 → 유저 trust_score 연동 (브랜치 `feat/a2-verified-trust`)
- 배경(docs/BACKLOG.md A2): `reports.verified`(V10, GPS 100m 방문인증)는 지금까지 **제보 단위**로만 place_report_signals 뷰에서 ×1.3 가중됐다. 방문인증 제보를 꾸준히 한 유저의 `trust_score`(V6 TrustScore 계보)를 **유저 단위**로도 올려, 그 유저의 이후 제보가 뷰 가중에서 더 실리는 선순환을 만든다(허위제보 억제 §0-7).
- 한 일: ① `TrustScore.calculate`에 4-arg 오버로드(`reportCount, reviewCount, verifiedCount, accountAgeDays`) 추가 — `verifiedBonus = min(verifiedCount, 20)`을 contributions에 additive(3-arg는 verifiedCount=0으로 위임, 하위호환). ② `ReportRepository.countByUserIdAndVerifiedTrue`(파생 쿼리, idx_reports_user 경로) 추가. ③ `TrustScoreService.recalculate`가 verified 수를 세어 전달. ④ 단위·IT 테스트.
- 왜(why): ① **왜 additive 보너스(별도 성분 아님)인가** — verified 제보는 그 자체가 이미 reportCount에 포함되므로, 보너스는 그 위에 추가 1 기여(=verified는 실질 2배 가중). 후기(2x)와 같은 "신호 강도 차등" 방식이라 기존 로그 스케일·곱(age 게이트) 구조를 그대로 재사용(공식 일관성). ② **왜 캡 20인가(§0-7 어뷰징)** — 한 장소에 눌러앉아 self-verify를 남발하는 어뷰징 차단. 레이트리밋(분3·시간10)+로그 포화가 1차 방어, 캡이 2차. 20이면 성실 인증 유저는 다 받고(이미 volume 상당) 무한 남발만 자른다. ③ **왜 온디맨드 재계산 유지인가** — 기존 TrustScoreService가 제보/후기 저장 직후 재계산(배치 아님, WORKLOG 2026-07-09 근거). verified 수 카운트 1회만 추가라 비용 미미, 새 트리거·스케줄 불필요(§0-2 과설계 금지). ④ **왜 곱 게이트를 verified가 못 넘는가** — verified 보너스도 volumeScore에만 기여하고 ageScore는 그대로라, age=0 신규 계정은 verified가 많아도 여전히 0(스팸 억제 목적 유지, 단위테스트 `verifiedCannotBypassAgeGate`로 고정).
- 스코프 규율: 하위호환 100% — 3-arg 오버로드가 verifiedCount=0으로 위임해 기존 호출부·테스트 전부 불변(Mockito 미스텁 long은 0L이라 기존 서비스 테스트도 그대로 pass). 뷰(V10 ×1.3 제보단위 가중)는 안 건드림 — A2는 유저단위 신호만 추가.
- 검증: 단위 green — `TrustScoreTest` 4건(3-arg 동치·verified 상승·캡 포화·age 게이트) + `TrustScoreServiceTest` 1건(verified 있으면 더 높음). IT — `GpsVisitVerifyIT`에 `countByUserIdAndVerifiedTrue` 실 DB 집계 1건(인증 2 + 비인증 1 → verified 2). 로컬 IT colima skip(TS-009) → **CI Backend(Gradle) pass 확인 후 머지**(TS-025).
- 산출물: `TrustScore`·`TrustScoreService`·`ReportRepository`(각 소폭) · 테스트 3파일. 마이그레이션 없음(파생 쿼리는 기존 idx_reports_user 경로).
- 관련: ADR-0005 §④(GPS 방문인증)·V6 trust 계보·V10 verified, CLAUDE.md §5·§0-7, docs/BACKLOG.md A2.
## 2026-07-10 — A4. 제보 급증 실시간 구독 + 지도 배지 (프론트) (브랜치 `feat/a4-surge-sse-frontend`)
- 배경(docs/BACKLOG.md A4): 백엔드 `GET /alerts/stream`(SSE)·`GET /alerts/surge?bounds=`(스냅샷)이 라이브(#44, ADR-0016)인데 프론트가 구독을 안 해 실시간 UGC 시공간(간판)의 체감이 비어 있었다. EventSource로 급증을 실시간 반영한다.
- 한 일: ① **SSE 스트리밍 BFF 프록시** `lib/backend.ts:proxyStream` — 기존 `proxy()`는 `res.text()`로 전량 버퍼링해 SSE 불가라, upstream body를 `new Response(body, {text/event-stream, no-cache/no-transform})`로 **패스스루**(ADR-0004). ② BFF 라우트 `app/api/alerts/{surge,stream}/route.ts`(stream은 `force-dynamic`). ③ `lib/surge.ts:useSurgeAlerts(bounds, onNew)` — 스냅샷 폴링(react-query 45s)을 신뢰 baseline으로, EventSource `surge` 이벤트를 실시간으로 병합(placeId dedupe, 라이브가 최신). ④ `components/map/SurgeBanner.tsx` — 상단 얇은 배너(중립 앰버·§6). ⑤ 홈 지도에 배너 + 뷰포트 안 새 급증 시 중립 토스트.
- 왜(why): ① **왜 SSE + 스냅샷 폴링 둘 다인가** — Vercel 함수 최대 실행시간에 SSE 연결이 끊길 수 있어(서버리스 한계) EventSource 자동 재연결 + 45s 스냅샷 폴링이 공백을 메운다(백엔드 ADR-0016이 설계한 "SSE 폴백" 그대로). ② **왜 body 패스스루인가** — SSE는 즉시 전달이 생명이라 버퍼링 프록시(text())로는 못 흘린다. `no-transform`으로 중간 버퍼링도 끈다. ③ **왜 뷰포트 밖 급증은 토스트 안 띄우나** — 지금 보는 지역과 무관한 급증으로 화면이 튀지 않게(onNew는 bounds 내부만). ④ **왜 §6 앰버·중립 문구인가** — 침수/벌레 급증도 "위험!"이 아니라 백엔드가 순화한 message("최근 침수 제보가 몰리고 있어요 · 우회 권장")를 그대로, 색도 경고 적색이 아닌 따뜻한 앰버로(공포 조장 금지). ⑤ **간판 안 가림(§0-9)** — 배너는 상단 얇게·접기 가능, 지도(간판)를 덮지 않는다.
- 규율/함정 대응: ref 갱신을 render가 아니라 effect에서(react-hooks/refs), 배너 접힘은 effect setState 없이 signature 파생으로(react-hooks/set-state-in-effect) — 둘 다 React 19 린트 규칙 준수. pointer-events 레이어링으로 배너/검색/필터 클릭 통과.
- 검증: `npm run lint`·`typecheck`·`build` 전부 green. `/api/alerts/{surge,stream}` 라우트 빌드 확인(ƒ dynamic). (프론트 CI=frontend-ci.yml, Vercel 프리뷰 배포.)
- 산출물: `types/alert.ts`(신규) · `lib/backend.ts`(proxyStream) · `lib/api.ts`(fetchSurge) · `lib/surge.ts`(신규 훅) · `components/map/SurgeBanner.tsx`(신규) · `app/api/alerts/{surge,stream}/route.ts`(신규) · `app/(shell)/page.tsx`.
- 관련: docs/BACKLOG.md A4, ADR-0016(급증 LISTEN/NOTIFY→SSE)·ADR-0004(BFF 프록시), CLAUDE.md §6(공포 조장 금지)·§0-9(간판 우선).

## 2026-07-10 — A5. 시간대별 혼잡 popular-times 히트맵 UI (프론트) (브랜치 `feat/a5-popular-times-heatmap`)
- 배경(docs/BACKLOG.md A5): 백엔드 `GET /places/{id}/popular-times`(요일×시간 혼잡 파생, Redis 1h 캐시, #45·#53)가 라이브인데 프론트 노출이 없었다. 상세 화면에 시간대별 혼잡을 보여준다.
- 한 일: ① `types/popular.ts`·`fetchPopularTimes`·`usePopularTimes`(staleTime 10m — 백엔드 1h 캐시) · BFF 라우트 `/api/places/[id]/popular-times`. ② `components/place/PopularTimes.tsx` — **요일 선택 + 선택 요일의 24시간 혼잡 스트립**(셀 색=혼잡 강도, 히트맵 인코딩). 현재 KST 시각 셀 ring 강조, 시간 눈금(0/6/12/18), 범례. ③ PlaceDetailOverlay에 RecentReports 다음 배치.
- 왜(why): ① **왜 7×24 히트맵이 아니라 하루치 스트립인가(dataviz 폼 선택)** — UGC 제보가 희소해 168칸 히트맵은 대부분 공백(노이즈). 모바일 상세 폭에도 안 맞는다. dataviz "데이터 job에 맞는 폼 + 희소 데이터 정직성" 원칙대로 **요일 선택 + 24시간 스트립**으로 압축(간판 안 가림 §0-9). 강도 인코딩(셀 색)은 유지. ② **왜 발산형(diverging) 색인가** — 그늘 사용자의 관심은 "자리 있나"라, 한산(자리 있음=좋음, teal) ↔ 붐빔(amber)의 **극성**이 "얼마나 바쁜가"(순차)보다 의미 있다. dataviz 발산 규약(두 극 + 중립 회색 중점) 준수. **`validate_palette.js`로 검증**: 두 극 CVD ΔE 19.8(>12 안전). ③ **왜 §6 amber(적색 아님)인가** — 붐빔도 "위험"이 아니라 주의 정도. ④ **왜 색만으로 구분 안 하나(접근성)** — 범례 + 셀 `aria-label`/`title`(시각·등급·제보수)로 relief 요건 충족(dataviz 대비 WARN 완화). ⑤ **왜 무데이터면 조용히 접나** — 로딩/에러/무데이터는 null 반환(살 섹션으로 상세를 채우지 않음, 간판 우선).
- 검증: `npm run lint`·`typecheck`·`build` green. `/api/places/[id]/popular-times` 라우트 빌드 확인. dataviz 스킬 규격 준수(폼 선택·발산 팔레트 검증·접근성 relief).
- 산출물: `types/popular.ts`·`lib/api.ts`·`lib/queries.ts`·`components/place/PopularTimes.tsx`·`app/api/places/[id]/popular-times/route.ts`·`PlaceDetailOverlay.tsx`.
- 관련: docs/BACKLOG.md A5, ADR-0005 §④(자체 popular-times), dataviz 스킬, CLAUDE.md §6·§0-9.

## 2026-07-10 — A6. 후기 커뮤니티 최소 UI (댓글·유용해요) (프론트) (브랜치 `feat/a6-community-ui`)
- 배경(docs/BACKLOG.md A6): 백엔드 `POST/GET /reviews/{id}/comments`·`POST/DELETE /reactions`(#49, V11)가 라이브인데 프론트 노출이 없었다. 상세 후기에 최소 댓글 + "유용해요" 토글을 연다. **규율: 커뮤니티는 살, 간판 아님(§0-9) — 최소 표면만.**
- 한 일: ① `types/community.ts`·`fetchReviewComments`·`createReviewComment`·`toggleReaction` · BFF 라우트 `/api/reviews/[id]/comments`(GET 공개+POST 로그인)·`/api/reactions`(POST/DELETE). ② `backend.ts` 인증 프록시를 `proxyAuthed(method,...)`로 일반화(리액션 취소=DELETE+body 지원, `proxyAuthedPost`는 래퍼로 호환). ③ `useReviewComments`(펼침 시 지연 로드)·`useCreateReviewComment`. ④ ReviewsSection에 후기별 footer: `HelpfulToggle`(👍) + `ReviewComments`(💬 접힘 기본).
- 왜(why): ① **왜 리액션이 상호작용 기반인가** — 백엔드 후기 응답에 reacted/count가 없어(ReviewResponse 확인) 초기 상태를 못 받는다. 상태를 읽으려 POST하는 건 부작용이라 금물 → 클릭 시 POST/DELETE 응답 {reacted,count}로만 표시(카운트를 과시하지 않음, §0-9와도 정합). ② **왜 댓글은 기본 접힘·지연 로드인가** — 커뮤니티가 전면에 나오면 리뷰앱이 돼 간판이 희석된다(§0-9). 펼쳤을 때만 `useReviewComments(enabled)`로 로드해 상세 진입 비용도 안 늘린다. ③ **왜 proxyAuthed 일반화인가** — 리액션 취소가 DELETE+body라 POST 전용 프록시로 부족. method 파라미터화로 DRY(기존 후기 POST 호환 유지). ④ **왜 로그인 게이팅을 프론트에서도 하나** — 비로그인 클릭은 백엔드 왕복 없이 "로그인 필요" 토스트(불필요 401 왕복 방지, 기존 후기 폼과 동일 UX).
- 스코프 규율(§0-9): 후기 footer의 작은 칩 2개(👍/💬)만 노출, survival_score(간판)와 무연결. 커뮤니티 카운트를 전면에 세우지 않는다.
- 검증: `npm run lint`·`typecheck`·`build` green. `/api/reactions`·`/api/reviews/[id]/comments` 라우트 빌드 확인.
- 산출물: `types/community.ts`·`lib/api.ts`·`lib/backend.ts`(proxyAuthed)·`lib/queries.ts`·`components/place/ReviewsSection.tsx`·`app/api/reactions/route.ts`·`app/api/reviews/[id]/comments/route.ts`.
- 관련: docs/BACKLOG.md A6, #49(커뮤니티 백엔드 V11), CLAUDE.md §8(커뮤니티=2차·살)·§0-9(간판 우선·리뷰앱화 금지).

## 2026-07-10 — A7. 관심 장소(bookmarks) 테이블·API·UI (V14) (브랜치 `feat/a7-bookmarks`)
- 배경(docs/BACKLOG.md A7): ERD `bookmarks(user_id, place_id, memo, created_at)`가 미구현이었다. 저장/해제 + 마이페이지 목록. **B1 알림의 "관심 장소 상태 변화"의 선행 테이블**이라 A에서 먼저.
- 한 일(백엔드): ① Flyway **V14** `bookmarks`(uq_bookmarks user×place, idx_bookmarks_user 최신순). ② `domain.bookmark`(Bookmark 엔티티·Repository·Service·Controller·BookmarkView 프로젝션·DTO 3종). API `POST /bookmarks`(멱등 upsert)·`DELETE /bookmarks/{placeId}`(멱등)·`GET /me/bookmarks`(장소 조인·최신순·soft-delete 제외). ③ SecurityConfig에 세 경로 authenticated 추가.
- 한 일(프론트): ④ `types/bookmark.ts`·api(fetch/add/remove)·queries(`useMyBookmarks`·`useToggleBookmark`)·BFF 라우트 3개(proxyAuthed 재사용, GET/POST/DELETE). ⑤ 상세 헤더에 `BookmarkButton`(★ 토글, 초기 상태=목록 멤버십). ⑥ 마이페이지 `BookmarksSection`(목록·항목 클릭 시 상세 오버레이 open — shell 레이아웃 공용).
- 왜(why): ① **왜 upsert(멱등)인가** — 저장 토글은 중복 저장이 무의미. uq_bookmarks + findByUserIdAndPlaceId로 memo 갱신(Review 장소당 1건 정책과 동형, 저빈도 UGC라 레이스 허용). ② **왜 BookmarkView 네이티브 조인인가** — 목록에 장소명·좌표·카테고리가 필요한데 bookmarks만으론 부족 → places 조인, 좌표 ST_Y/ST_X 평탄화, created_at Instant→UTC(TS-016). soft-delete 장소 제외(폐업 회전, ADR-0006). ③ **왜 리액션과 달리 초기 상태를 아는가** — bookmarks는 `GET /me/bookmarks` 목록이 있어 useMyBookmarks 멤버십으로 별 채움을 정확히 표시(리액션은 GET이 없어 상호작용 기반이었던 것과 대비). ④ **왜 존재 안 하는 장소는 404인가** — FK 위반 500 대신 명확한 신호(existsById 선검사). ⑤ **왜 살(개인화)이고 간판 아님인가** — survival_score와 무연결(§0-9). B1 알림이 이 테이블을 재사용할 선행.
- 검증: 단위 green — `BookmarkServiceTest` 4건(신규 save·재저장 upsert·404·해제 멱등). IT — `BookmarkFlowIT` 5건(저장→목록 조인·재저장 멱등·해제 후 사라짐·비로그인 401·없는 장소 404, 실 PostGIS + Security 필터체인·JWT). 프론트 lint/typecheck/build green(3 라우트 확인). 로컬 IT colima skip(TS-009) → **CI Backend(Gradle) pass 확인 후 머지**(TS-025). **V14 프로덕션 적용은 머지 후 자동 배포(Flyway).**
- 산출물: `V14__bookmarks.sql` · `domain/bookmark/*`(8파일) · `SecurityConfig`(3매처) · 백엔드 테스트 2파일 · 프론트 `types/bookmark.ts`·`lib/api.ts`·`lib/queries.ts`·`components/place/BookmarkButton.tsx`·`PlaceDetailOverlay.tsx`·`mypage/page.tsx`·BFF 라우트 3개.
- 관련: docs/BACKLOG.md A7, ERD §8, ADR-0006(soft-delete)·TS-016(Instant), CLAUDE.md §0-9(살). **B1 알림 "관심 장소 상태 변화"의 선행 완료.**

## 2026-07-10 — B1. 알림(Notifications) — 인앱 센터 + 급증 이벤트 재사용 (V15) (브랜치 `feat/b1-notifications`, ADR-0018)
- 배경(docs/BACKLOG.md B1): 심화 알림 — ① 주변 급증, ② 관심 장소 변화(A7), ③ 폭염 피난. ERD notifications 미구현. **ADR 선행**(전달 방식 웹 트렌드 확인).
- 결정(ADR-0018): ① **전달=인앱 센터 MVP, Web Push는 stretch** — 2026 웹 검색 확인: iOS Web Push는 "홈 화면 추가 PWA"에서만·Safari 탭 불가·EU DMA 제약 → 도달률이 설치 PWA로 제한. 공개 커먼스엔 인앱이 먼저(BACKLOG 권장 일치). ② **평가=급증 LISTEN/NOTIFY(ADR-0016) 재사용** — 새 스케줄러 없이(§0-2) `ReportNotificationListener`가 급증 확인 직후 `NotificationService.onSurge` 호출. ③ **중복 없이 1회=dedup_key UNIQUE** — key=`surge:{ruleId}:{placeId}:{epoch/10min}`, ON CONFLICT DO NOTHING이 **멀티 인스턴스 중복 + cooldown**을 동시에 막는다.
- 한 일(백엔드): V15 `notification_rules`(규칙)+`notification_deliveries`(발송·dedup UNIQUE). `domain.notification`(엔티티 2·Repository 2·Service·Controller·DTO 4). 규칙 타입 **SURGE_NEARBY**(center+radius, ST_DWithin 매칭)·**BOOKMARK_SURGE**(북마크 조인, A7 재사용)·**HEAT_ESCAPE**(타입만 정의·평가 follow-up). API: 규칙 CRUD(`POST/GET/PATCH/DELETE /notifications/rules`) + 센터(`GET /notifications`·`POST /notifications[/{id}]/read`). 매칭 발송은 네이티브 INSERT…SELECT(ST_DWithin/북마크 조인)+ON CONFLICT. SecurityConfig 보호. 리스너에 onSurge 배선.
- 한 일(프론트): `types/notification.ts`·api 6종·queries(useNotifications 폴링 60s·useNotificationRules·토글·읽음)·BFF 라우트 5. 마이페이지 `NotificationsSection`(알림 센터 목록·모두읽음·안읽음 배지 + 규칙 토글 2종 스위치).
- 왜(why): ① **왜 구조화 컬럼(condition_json 아님)** — 규칙 매칭이 공간쿼리(ST_DWithin)라 JSONB 추출은 취약·비효율. center_lat/lng/radius_m 구조화가 타입안전·인덱스·테스트 유리(ERD 초안에서 의도적 이탈, ADR-0018 문서화). ② **왜 매칭을 네이티브 INSERT…SELECT로** — 규칙 필터(공간·북마크 조인)+발송+dedup을 한 문 원자 처리(앱 루프 없이 DB에서, 간판 정신모델). ③ **왜 폴링 센터(SSE 아님)** — 센터는 "없을 때 쌓인 것" 이력 조회라 영속 폴링이 맞다(A4 SSE는 실시간 순간). ④ **왜 §6** — 발송 문구=급증 SurgeInfo.message(이미 순화) 재사용("위험!" 금지).
- 검증: 단위 green `NotificationServiceTest` 4건(SURGE_NEARBY 좌표검증 400·BOOKMARK 좌표불요·onSurge 양쪽 매칭 호출·토글 404). IT `NotificationFlowIT` 5건(규칙→급증→인앱 1건 중복없이→읽음·반경밖 무발송·BOOKMARK 조인·401·400, 실 PostGIS+Security+JWT). 전체 `./gradlew test` green(ITs colima skip, CI 게이트 TS-025). 프론트 lint/typecheck/build green(5 라우트). **V15는 머지 후 Flyway 자동 적용.**
- 산출물: `V15__notifications.sql` · `domain/notification/*`(10파일) · `ReportNotificationListener`(onSurge 배선)·`SecurityConfig` · 백엔드 테스트 2 · 프론트 `types/notification.ts`·api·queries·`NotificationsSection.tsx`·`mypage`·BFF 5 · ADR-0018.
- 관련: ADR-0018, ADR-0016(급증 LISTEN/NOTIFY 재사용)·A7 bookmarks(BOOKMARK_SURGE)·ADR-0013(멀티 인스턴스), CLAUDE.md §3·§6·§9. **follow-up: HEAT_ESCAPE 평가(날씨 트리거)·Web Push(VAPID) stretch.**

## 2026-07-10 — B2. 루트 — 화장실 포함 경로 MVP (브랜치 `feat/b2-routes-toilet`, ADR-0019)
- 배경(docs/BACKLOG.md B2): 심화 루트 — 화장실 포함/그늘/비 경로. 자체 라우팅(pgRouting)은 과설계(§0-2) → 외부 경로 API + 우리 POI 오버레이. **ADR 선행**(외부 API 결정).
- 결정(ADR-0019): ① **경유지 선택=우리 PostGIS(간판)** — 출발→화장실→도착 detour 최소 화장실을 midpoint corridor(ST_DWithin, V3 GIST)로 좁혀 (출발거리+도착거리) 최소로 고른다(`findBestToiletWaypoint`). ② **도로 폴리라인=외부 directions API** — 2026 확인: 카카오모빌리티 다중경유지(`/v1/waypoints/directions`, 키·활용신청·쿼터). **키·TS-026 계약검증이 사용자 액션**이라 지금 shipping 안 함 → `DirectionsProvider` 전략 추상화 + `StraightLineDirectionsProvider`(직선 MVP·`mode=straight`, 키 없이 동작·테스트). 키 붙으면 Kakao 공급자 빈 교체만으로 `mode=road` 승격(RouteService 불변). ③ **그늘/비 경로=스코프만 기록**(follow-up, 자체 가중 라우팅 §0-2 지양).
- 한 일(백엔드): `domain.route`(RouteController `/routes/toilet`·RouteService·RouteWaypointView·DirectionsProvider+StraightLine·DTO). `PlaceRepository.findBestToiletWaypoint`(공간쿼리). `GeoUtils.haversineMeters`(corridor 반경 산정용 근사). 공개 엔드포인트(로그인 불필요). 한 일(프론트): `types/route.ts`·`fetchToiletRoute`·BFF `/api/routes/toilet` + 상세 "화장실 들러 가기" 버튼(경유 화장실·총거리 안내).
- 왜(why): ① **왜 경유지는 우리, 폴리라인만 외부인가** — 화장실 detour 최소 선택은 대용량 공간검색(간판)이라 우리가 소유하는 게 차별점. 외부엔 도로 폴리라인만 위임(최소 의존). ② **왜 미검증 Kakao 클라이언트를 안 심나(TS-026)** — 외부 계약은 실호출 검증 전 신뢰 금지(storeapi 전례). 전략 추상화로 직선 MVP를 기본으로, Kakao는 키 확보 후 계약검증하며 얹는다. ③ **왜 corridor 선필터인가** — 화장실 5만+ 전체 detour 정렬은 전스캔 → midpoint 반경(직선/2+1.5km) ST_DWithin으로 후보를 좁혀 인덱스 경로 유지(§0-4). ④ **왜 지도 폴리라인 오버레이가 후속인가** — 도로 폴리라인은 키 필요(직선은 시각적으로 덜 유용), 지도 SDK 폴리라인 오버레이는 별개 작업 → MVP는 경유 화장실·총거리 안내로 end-to-end 성립(API IT가 경유지·폴리라인 검증).
- 검증: 단위 `RouteServiceTest` 2건(경유 3점·총거리=출발+도착 / 무경유 2점·직선거리). IT `RouteToiletIT` 3건(사이 화장실 경유·polyline 3점·mode straight / corridor 밖만 있으면 무경유 2점 / 해외좌표 400, 실 PostGIS). 전체 `./gradlew test` green. 프론트 lint/typecheck/build green(라우트 확인). 마이그레이션 없음(경로는 온디맨드 계산).
- 사용자 입력 지점(BACKLOG 명시): **카카오모빌리티 활용신청·키**(도로 폴리라인 `mode=road` 승격 시). 키 확보 시 `DirectionsProvider` Kakao 구현 + TS-026 실호출 계약검증 + `.local`/SSM 키.
- 산출물: `domain/route/*`(7파일) · `PlaceRepository`(waypoint 쿼리)·`GeoUtils`(haversine) · 백엔드 테스트 2 · 프론트 `types/route.ts`·`lib/api.ts`·`app/api/routes/toilet`·`PlaceDetailOverlay` · ADR-0019.
- 관련: ADR-0019, ADR-0001(공간 인덱스)·ADR-0004(BFF), TS-026(외부 계약검증), CLAUDE.md §0-2·§6. **follow-up: Kakao 도로 폴리라인·지도 오버레이 UI·그늘/비 경로.**

## 2026-07-10 — 데이터 op 실행: A3 쉼터 냉방 재적재 · A8 상권 6대 광역시 확장 · B1 Web Push VAPID (사용자 승인 후)
사용자 승인("할 수 있는거 전부 다 해")으로 코드 완료 후 남은 대량 데이터 op·키 항목을 실행. (코드 PR은 A3 #63·A8 스크립트 기존·B1 #68에서 이미 머지됨 — 여기는 **실행 기록**.)

### A3 — 쉼터 air_conditioned 실 재적재 (완료·라이브 검증)
- **한 일**: safetydata `DSSP-IF-10942`를 **등록 IP(로컬)**에서 전량 재다운로드하되 **냉방기 보유수(COLR_HOLD_ARCNDTN) 컬럼 포함** CSV로 생성(헤더=`SourceSpec.COOLING_SHELTER` 별칭 + 냉방 컬럼, 주소 per-row 폴백 RN_DTL_ADRES→DTL_ADRES) → GitHub Release `data-v1/shelters.csv` 갱신(6.96MB) → ECS `prod-ingest.sh cooling_shelter <url> UTF-8` 재적재.
- **결과(실측 로그)**: `source=cooling_shelter_std total=60,297 upserted=60,297 skipped=0 geocoded=0 deactivated=0 **featuresBackfilled=57,070**`(전국 쉼터의 95%가 냉방기 보유). A3 조건부 백필 코드(#63)가 냉방기>0 쉼터에 air_conditioned(conf 0.4) 부여.
- **라이브 검증**: 쉼터 상세(휴서울이동노동자북창쉼터) `features=[air_conditioned/PRESENT/냉방/POSITIVE/0.4]` + **무제보인데 survival.comfortScore=0.353**(A1 시설-comfort 통합이 쉼터에서 실동작 — place_feature_signals 뷰→SurvivalScore 단조 상승), 등급은 §9대로 UNKNOWN 유지. **A1+A3 합작 완성.**
- **왜 CSV 우회인가**: safetydata 키가 발급 IP에 잠겨 ECS egress 불가(TS-027) → 등록 IP 로컬 다운로드 + 기존 CSV 파이프라인이 유일 경로. 이번엔 냉방 컬럼을 실어 A3 백필을 활성화.

### A8 — 상권 카페/스터디카페 6대 광역시 확장 (완료)
- **한 일**: `prod-ingest-stores.sh <bbox> 1500`로 부산·대구·인천·대전·광주·울산 도심 격자 적재(멱등). data.go.kr B553077, 좌표 내장(geocoded 0).
- **함정 발견·대응**: 6개를 병렬 실행하니 **`IngestBatchLock`(동시 실행 방지)**에 걸려 뒤 3개가 skip(exitCode 0이나 미적재) → **순차 실행**으로 정정(락 존중, 한 번에 하나).
- **결과(세션 upsert, 격자 중복 포함)**: 부산 11,877(CAFE 10,965·STUDY 912) · 대구 7,136 · 대전 5,677 · 인천 5,869(CAFE 5,409·STUDY 460) · 광주 4,555(CAFE 4,110·STUDY 445) · 울산 2,504(CAFE 2,304·STUDY 200) = **≈37,618행 / 6대 광역시**. data.go.kr 일일 쿼터 에러 0(약 372셀). 서울(기존 distinct 29,886) + 6대 광역시 전부 CAFE 커버리지 실측 확인.
- **남음**: 나머지 중소도시는 일일 쿼터·시간상 후속(멱등이라 하루 단위로 이어서 실행 — `prod-ingest-stores.sh <bbox>`).

### B1 — Web Push VAPID (키 생성 완료, 전송 배선은 후속)
- **한 일**: `npx web-push generate-vapid-keys`로 VAPID 키쌍 생성 → `.local/webpush.env`(gitignore). 공개키(87자)는 프론트 임베드용, 개인키는 비밀(§D).
- **왜 전송 미배선인가**: 전체 Web Push 전송은 (1) 무거운 크립토 라이브러리(BouncyCastle) 신규 의존성 (2) **실기기(설치형 PWA) 없이는 end-to-end 검증 불가**. 검증 불가한 코드를 클린한(전부 green) 프로덕션에 blind로 넣는 것은 규율 위반(TS-026 정신·"검증 안 된 것 shipping 금지") → ADR-0018에서 이미 stretch로 분류. **키는 준비 완료**, 배선은 실기기 검증과 함께 후속. 인앱 알림 센터(B1 본체)는 이미 라이브.

## 2026-07-10 — F5: HEAT_ESCAPE 폭염 피난 알림 평가 (ADR-0020)
- 한 일(백엔드): `HeatComfort.isHeatAdvisory(Weather)`·`feelsLike(Weather)` 공개 추가(체감 ≥33℃ 폭염주의보 판정, 기존 공식 재사용). `NotificationService.evaluateHeatEscape(userId)` — 활성 HEAT_ESCAPE 규칙별로 규칙 중심 날씨→폭염이면 kNN `findNearest(COOLING_SHELTER,1)`→3시간 버킷 dedup `heat:ruleId:bucket`으로 `insertHeatEscape` 1건. `NotificationController.list`가 목록 직전 온디맨드 평가(별도 트랜잭션). `createRule` HEAT_ESCAPE=lat/lng 필수 검증. `NotificationDeliveryRepository.insertHeatEscape`(단일행 ON CONFLICT DO NOTHING)·`NotificationRuleRepository.findByUserIdAndTypeAndActiveTrue`. 한 일(프론트): `NotificationsSection` "폭염 피난 추천" 토글(현재 위치=판정 중심, 반경 없음).
- 왜(why): ① **왜 온디맨드(pull)인가 — 새 스케줄러 안 씀(§0-2)**: Web Push(F2) 미배선이라 발송 이력은 앱을 열어야만 보인다 → 서버 주기생성해도 유저가 보는 결과가 온디맨드와 동일한데 인프라(EventBridge→RunTask·전유저 스캔)만 늘어난다. 읽기 진입점 멱등 upsert(dedup UNIQUE)라 반복 열람·멀티 인스턴스에 안전. 푸시 배선 시 트리거만 주기평가로 승격(로직 재사용). ② **왜 체감 33℃인가**: 기상청 폭염주의보 발효선. `HeatComfort`가 이미 33/35/38에 앵커된 체감온도 공식을 가지므로 임계값을 추측 않고 재사용(§0-B). ③ **왜 kNN 재사용인가**: 근처 쉼터 = 간판 공간검색(<->), 새 쿼리 안 만듦. ④ **왜 3시간 cooldown인가**: 날씨 실황은 시간당 갱신이라 매시 알림은 나깅 → 규칙당 3시간 1회. ⑤ **§6 표현**: "위험!" 금지, "지금 체감 34℃, 가까운 무더위쉼터 '○○'(120m)에서 잠깐 쉬어가세요" 권유형. graceful: 날씨 결측·쉼터 없음·한 규칙 예외는 조용히 skip(다른 규칙·목록 안 막음).
- 검증: 단위 `NotificationServiceTest` — HEAT_ESCAPE 검증(lat/lng 필수 400)·폭염+쉼터→insert·폭염 아님 skip·쉼터 없음 skip·규칙 없음 no-op. 프론트 tsc green. CI 게이트(NotificationFlowIT 실 PostGIS). 마이그레이션 없음(기존 notification_deliveries·notification_rules 재사용).
- 산출물: `HeatComfort`·`NotificationService`·`NotificationController`·`NotificationDeliveryRepository`·`NotificationRuleRepository` · 프론트 `NotificationsSection` · ADR-0020 · 테스트.
- 관련: ADR-0020, ADR-0018(알림 본체)·ADR-0009(체감 comfort)·ADR-0001(kNN), CLAUDE.md §0-2·§6. **BACKLOG F5 완료. follow-up 없음(F2 푸시 배선 시 트리거 승격 지점만 기록됨).**

## 2026-07-10 — F1: 상권 카페/스터디카페 9개 도시 확장 실행 (데이터 op)
사용자 승인("전부 다 모두 다 해")으로 F1(A8 나머지 지역) 실행. `prod-ingest-stores.sh <bbox> 1500` 순차(IngestBatchLock 존중). data.go.kr B553077, 좌표 내장(geocoded 0).
- **실행·결과(세션 upserted, 격자 중복 포함)**: 수원 4,000(CAFE 3,629·STUDY 371) · 성남·용인 4,055 · 창원 2,672 · 청주 2,900 · 전주 3,401 · 천안 2,479 · 포항 1,667 · 김해 1,338 · 제주 1,692 = **≈24,204행 / 9개 도시**. **쿼터 에러 0**(cells 15~56/도시). 라이브 검증: 수원·창원·전주·제주 반경 2km CAFE 100(cap 도달, 이전 0).
- **왜 순차인가(재확인)**: `IngestBatchLock`이 동시 실행을 막아 병렬 시 뒤 태스크가 skip(exitCode 0이나 미적재, A8 6대 광역시 때 발견한 함정). 드라이버가 각 RunTask 완료(`aws ecs wait`) 후 다음을 실행. 쿼터 소진 감지(resultCode/upserted=0) 시 중단하도록 짰으나 9개 전부 여유 내 완료.
- **커버리지 현황**: 상권 = 서울(distinct 29,886) + 6대 광역시(부산·대구·인천·대전·광주·울산) + **9개 도시(수원·성남·용인·창원·청주·전주·천안·포항·김해·제주)**. 남은 중소도시는 후속(멱등, 하루 단위로 `prod-ingest-stores.sh <bbox>` 이어서).
