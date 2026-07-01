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
