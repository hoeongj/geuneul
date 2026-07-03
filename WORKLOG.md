# 그늘 (Geuneul) — 작업 로그 (WORKLOG)

> **목적:** 면접·서류 제출 시 심사자가 읽고 "근거 있게, 감각 있게 개발했다"고 느끼게 하기 위한 개발 일지.
> 언제 무슨 작업을 했는지 + **스펙·방법·로직·알고리즘·라이브러리를 왜 그렇게 골랐는지(why)** 를 남긴다.

## 작성 규칙
- **작업할 때마다 맨 아래에 append.** 덮어쓰지 말고 시간순으로 계속 쌓는다.
- 결정(스펙/라이브러리/알고리즘/구조)을 내릴 때마다: **선택 · 이유 · 검토한 대안 · 트렌드 근거(웹검색 결과)** 를 반드시 기록.
- 판단 1순위 = "포트폴리오에 넣기 좋은가" → 그다음 프로젝트 적합성 → 2026 트렌드/베스트프랙티스 부합.
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
- 한 일: GitHub public 레포 생성(ghdtjdwn/geuneul) + push → **CI 실제 GREEN**(GitHub 러너에서 PostGIS/Redis 통합테스트·Flyway·GiST까지 통과, 1m33s). 이후 배포(CD) 방식을 포트폴리오 기준으로 재검토.
- 결정 & 이유(why):
  - **mp의 k3s+ArgoCD+Helm GitOps를 그늘에 복제하지 않기로.** 판단 근거(기준 1순위=포트폴리오): mp가 이미 자가호스팅 k8s/GitOps를 최고 수준으로 증명 → 그늘이 동일 반복 시 **새 신호 0 + 솔로에 무거운 인프라 2벌 = 오버엔지니어링 신호**(mp의 "필요없는 Kafka 거부" 성숙 신호와 모순). 오히려 기준 1순위에 어긋남을 발견하고 방향 전환.
  - **PaaS 경량 채택: 백엔드 Railway + DB Supabase(PostGIS) + 프론트 Vercel.** 대안 검토: (A)기존 k3s에 thin-add=인프라 일관성이나 단일노드(4OCPU/24GB) 용량 리스크+PostGIS 클러스터 구축 필요, (C)배포 보류. → PaaS가 (1)mp와 다른 배포 패러다임=**breadth**, (2)관리형 PostGIS로 용량 리스크 제거, (3)에너지를 간판(지리공간 백엔드)에 집중, (4)push→자동배포 즉시.
  - **호스트 선택 근거(2026 웹검증):** Railway = GitHub push 자동감지·자동배포, 실질 무료 크레딧 유지. Fly.io = 2024 무료티어 폐지(CC 필수·트라이얼만) → 배제. Supabase = PostGIS 등 확장 기본 내장(Neon은 serverless라 일부 확장 제약) + pgrouting(향후 루트 기능) 보유 → Neon보다 우위.
  - **진짜 새 DevOps 신호는 P4로 이연** — mp가 안 한 것(오토스케일링/HPA를 k6 부하테스트로 증명)이 additive. 지금 억지 배포 심화 대신, 부하 스토리와 함께 만들 때 가치.
- 트렌드 근거(웹검색): Railway Spring Boot 배포 가이드/무료티어, Fly.io 무료티어 폐지, Supabase vs Neon PostGIS 확장 비교(2026).
- 관련: `CLAUDE.md`(§7 인프라·§10 P5 개정), 레포 `github.com/ghdtjdwn/geuneul`, CI run 28539003609(success)
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
- 수정한 결함(면접관이 코드와 대조하면 잡힐 것들):
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
