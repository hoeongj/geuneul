# 그늘 (Geuneul) — 여름 생존 지도

> 폭염·장마·벌레·화장실·식수·냉방·콘센트까지, **오늘 밖에서 살아남기 위한 생활 생존 지도.**
> "지금 쉬어갈 그늘을 찾아드립니다."

[![CI](https://github.com/hoeongj/geuneul/actions/workflows/ci.yml/badge.svg)](https://github.com/hoeongj/geuneul/actions/workflows/ci.yml)
[![Frontend CI](https://github.com/hoeongj/geuneul/actions/workflows/frontend-ci.yml/badge.svg)](https://github.com/hoeongj/geuneul/actions/workflows/frontend-ci.yml)
[![Deploy (AWS ECS)](https://github.com/hoeongj/geuneul/actions/workflows/deploy.yml/badge.svg)](https://github.com/hoeongj/geuneul/actions/workflows/deploy.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-informational.svg)](./LICENSE)

🟢 **App** — <https://geuneul.vercel.app> ·  🟢 **API** — [ALB `/actuator/health`](http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com/actuator/health) · [Swagger](http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com/swagger-ui.html)

---

## 무엇 · 왜

지도에 위치만 찍지 않고 **"지금 상태"(시원함/자리/콘센트/벌레/침수)** 를 최근 제보 기준으로 보여준다.
기존 공공지도는 "여기 화장실 있음"까지지만, 그늘은 **"지금 앉을 수 있는지, 시원한지, 붐비는지"** 를 실시간으로 답한다.

**핵심 차별점**
- **PostGIS 대용량 지리검색** — 반경(`ST_DWithin`)·최근접(kNN `<->`)·bounds를 GiST 인덱스로. 전국 공공데이터를 그대로 적재(공중화장실 46,897건 등).
- **실시간 UGC 시공간 스코어링(`survival_score`)** — 유효 제보를 최근성 × 신뢰도로 SQL 뷰에서 집계 → 마커 3색·"지금 갈만함" 점수.
- **시나리오 추천** — survival_score에 시나리오 가중을 얹어 "화장실 급함 / 잠깐 쉬어갈 곳 / 비 피할 곳"을 2단(공간 선필터 → 재랭킹)으로 정렬.

## 아키텍처

```
 ┌────────────────────────┐   동일 오리진    ┌───────────────────────────────────────────┐
 │  Browser (PWA)         │   /api/* 프록시   │  Spring Boot 4 / Java 21  (ECS Fargate)   │
 │  Next.js · Vercel      │ ───────────────▶ │                                           │
 │  Kakao Maps            │  (BFF, ADR-0004) │   반경 ST_DWithin · kNN <->  (GiST 인덱스) │
 └────────────────────────┘                  │   survival_score (SQL 뷰 + 순수 함수)      │
                                             │   시나리오 추천 2단 랭킹 · 멱등 ETL+지오코딩 │
                                             └───────────────┬───────────────────────────┘
                                          Hibernate Spatial+JTS │
                                                               ▼
                                          PostgreSQL + PostGIS (RDS)   ·   Redis (cache)

 외부: Kakao 지오코딩(주소→좌표) · 기상청(날씨) · Claude(요약, 곁다리)
 인프라: Terraform(IaC) · GitHub Actions OIDC(키 없는 배포) · ECR · ALB   → 배포 상세 DEPLOY.md
```

브라우저는 항상 **동일 오리진 `/api/*` 서버 프록시(BFF)** 만 호출한다 → ALB(http)·CORS 제약을 동시에 회피(백엔드 CORS 불필요, [ADR-0004](./docs/adr/0004-frontend-same-origin-proxy.md)).

## `survival_score` — 간판이 작동하는 방식

"지금 이 장소가 갈만한가"를 0~100점 + 3색 등급(초록 좋음 / 노랑 보통 / 회색 정보 부족)으로 낸다.

```
survival_score = 0.25·distance + 0.20·comfort + 0.20·freshness − 0.15·risk   (+ open_now: 데이터 붙으면 복원)
freshness 버킷:  0~1h=1.0 | 1~3h=0.8 | 오늘=0.6 | 이번주=0.3 | 그 외=0.1
```

- **시공간 집계는 SQL 뷰**(`place_report_signals`)가, **가중치 조립·등급은 순수 함수**가 담당한다 — 무거운 집계는 DB, 자주 튜닝하는 정책은 테스트 가능한 Java로 분리([ADR-0007](./docs/adr/0007-survival-score-sql-signals-java-compose.md)).
- 제보는 **신뢰도(trust) 가중**, **만료(휘발성)** 제외, 후기(영구 평판)와 분리.
- 없는 성분(운영시간 `open_now`)은 **지어내지 않고 재정규화** — 데이터가 붙으면 가중치 복원만으로 additive 확장.
- 추천은 같은 함수를 **시나리오 가중치로 재사용**한다([ADR-0008](./docs/adr/0008-recommendations-scenario-weighted-ranking.md)).

## 빠른 시작 (로컬)

```bash
# 1) 인프라 — PostGIS + Redis
docker compose up -d

# 2) 백엔드 — http://localhost:8080/swagger-ui.html
cd backend && ./gradlew bootRun

# 3) 프론트 — http://localhost:3000  (Kakao JS 키 없으면 지도는 placeholder, 데이터는 정상)
cd frontend && pnpm install && pnpm dev
```

공공데이터 적재는 멱등(재실행해도 중복 없음):

```bash
cd backend
./gradlew bootRun --args='--ingest.source=cooling_shelter --ingest.file=/path/무더위쉼터.csv --ingest.charset=MS949'
```

## API 맛보기

```bash
# 반경 검색 — 가까운 순 + 거리(m) + survival 배지
GET /places?lat=37.4963&lng=126.9575&radius=1000

# 뷰포트(bounds) 마커 — 무더위쉼터
GET /places?bounds=126.93,37.49,126.97,37.52&category=COOLING_SHELTER

# 최근접(kNN) — 서울 밖 어디서나 동작 (예: 부산)
GET /places/nearest?lat=35.2133&lng=129.0157&category=COOLING_SHELTER&limit=3

# 시나리오 추천 — 거리+실시간 상태로 "지금 갈만한 순" (scenario: rest30|restroom|rain)
GET /recommendations?lat=37.4963&lng=126.9575&scenario=restroom
#   → 각 결과 = 장소(survival 배지) + matchScore(적합도) + reason(제보 요약)

# 휘발성 제보 (익명, 타입별 TTL로 자동 만료 · 분당 3·시간당 10 레이트리밋)
POST /reports   {"placeId":1,"reportType":"COOL","comment":"에어컨 빵빵"}
GET  /places/1/reports
```

## 기술 스택

| | |
|---|---|
| **Backend** | Spring Boot 4 · Java 21 · PostgreSQL + **PostGIS**(Hibernate Spatial + JTS) · Flyway · Redis |
| **Frontend** | Next.js 16(App Router) · TypeScript · Tailwind v4 · TanStack Query · Kakao Maps · Serwist(PWA) — `frontend/` ([README](./frontend/README.md)) |
| **Infra** | AWS ECS Fargate · RDS · **Terraform** · GitHub Actions(OIDC) · ECR · ALB · Vercel |
| **Test/Ops** | Testcontainers(실 PostGIS) · JaCoCo · gitleaks · Swagger |

## 문서

- **아키텍처·의사결정 기록**: [`docs/adr/`](./docs/adr) (ADR 0001–0008, [색인](./docs/adr/README.md))
- **배포(AWS)**: [`DEPLOY.md`](./DEPLOY.md) · **현황·다음 작업**: [`HANDOFF.md`](./HANDOFF.md)
- **개발 일지**: [`WORKLOG.md`](./WORKLOG.md) · **트러블슈팅**: [`TROUBLESHOOTING.md`](./TROUBLESHOOTING.md)
- 전체 목표·범위·ERD·API 스펙: [`CLAUDE.md`](./CLAUDE.md)

<details>
<summary><b>구현 이력 (W0 → P4 완주)</b></summary>

- **W0** — Spring Boot 4 + PostGIS/Flyway + Testcontainers CI + AWS(ECS Fargate·RDS·Terraform·OIDC) 배포 파이프라인.
- **P1** — 반경/kNN/bounds 공간검색 API 라이브 + 공공데이터 멱등 인제스천(쉼터 100건 + 공중화장실 59,768행 파서 → 카카오 지오코딩으로 46,897건 좌표 보완).
- **P2 · UGC+인증** — 휘발성 제보(11타입·타입별 TTL, 레이트리밋 XFF/OOM 하드닝 TS-008) · **소셜 로그인**(카카오/구글 OAuth2+JWT, BFF code 교환) · **후기(review)** 영구 평판(로그인, 장소당 1건 upsert) · **사진 presign**(S3 SigV4, image 화이트리스트) · **trust_score** 실배선(로그인 제보 user_id 가중, V6) · **모더레이션**(신고 + ADMIN 검수 큐, V7).
- **P3 · 스코어·추천·AI·데이터** — `survival_score`(SQL 뷰 + 순수 함수, 마커 3색, [ADR-0007](./docs/adr/0007-survival-score-sql-signals-java-compose.md)) · 시나리오 추천 [ADR-0008](./docs/adr/0008-recommendations-scenario-weighted-ranking.md) · **날씨**(기상청 초단기실황 + Redis TTL 캐시) + comfort 복원([ADR-0009](./docs/adr/0009-weather-comfort-additive-restore.md)) · **AI 한줄요약**(OpenRouter, graceful degradation, [ADR-0010](./docs/adr/0010-ai-summary-openrouter-provider.md)) · **공부공간 데이터 확장**(CAFE/STUDY_CAFE·soft-delete, V5, [ADR-0006](./docs/adr/0006-study-space-coverage-expansion.md)) · **주기동기화**(EventBridge→ECS RunTask + advisory lock, [ADR-0011](./docs/adr/0011-scheduled-public-data-sync.md)).
- **P4 · 심화(간판 보강)** — **k6 부하테스트 + EXPLAIN 인덱스 튜닝**(반경/kNN/bounds GiST 사용 실증, V8 만료제보 인덱스, [ADR-0012](./docs/adr/0012-k6-load-explain-index-tuning.md)) · **ECS Service Auto Scaling**(CPU target-tracking, [ADR-0013](./docs/adr/0013-ecs-service-autoscaling.md)) · **관측성**(Micrometer/Prometheus + OTel 트레이싱 + 로컬 Grafana/Tempo, [ADR-0014](./docs/adr/0014-observability-otel-micrometer-grafana.md)).

</details>

## License

[MIT](./LICENSE) © 2026 hoengj
