# 그늘 (Geuneul)

> 여름 생존 지도 — 폭염·장마·벌레·화장실·식수·냉방·콘센트까지, 오늘 밖에서 살아남기 위한 생활 생존 지도.
> "지금 쉬어갈 그늘을 찾아드립니다."

지도에 위치만 찍지 않고 **"지금 상태"(시원함/자리/콘센트/벌레/침수)**를 최근 제보 기준으로 보여준다.
핵심 = `survival_score`(지금 갈만함 점수) + 최근성 기반 실시간 체감 정보.

- 커버리지: 전국 공공데이터 그대로 적재(지도가 필터링) · UGC 필드테스트: 서울 동작구
- 스택: Next.js(PWA) · Spring Boot 4/Java 21 · PostgreSQL+PostGIS(Hibernate Spatial) · Redis · 카카오/구글 OAuth · Kakao Maps · Claude API
- 데이터: 무더위쉼터/공중화장실 표준데이터/서울시 공원음수대/기상청 + 유저 제보

## 시작하기
전체 목표·범위·ERD·API·로드맵은 **[`CLAUDE.md`](./CLAUDE.md)** 참고 (새 세션이 자동으로 읽음).

## 상태
🟢 **API Live** — [http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com](http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com/actuator/health) (AWS ECS Fargate + RDS PostGIS, `main` push 시 자동배포)
🟢 **App Live** — **[https://geuneul.vercel.app](https://geuneul.vercel.app)** (프론트엔드 PWA, Vercel · Kakao 실지도 + 라이브 데이터)

- W0 완료(2026-07-02): Spring Boot 4 + PostGIS/Flyway + Testcontainers CI + **AWS(ECS Fargate·RDS·Terraform·OIDC) 배포 파이프라인**
- P1: **반경(ST_DWithin)/kNN(`<->`)/bounds 공간검색 API 라이브** + **공공데이터 idempotent 인제스천**(무더위쉼터 전국 샘플 100건 적재 · 공중화장실 표준데이터 59,768행 파서 + **카카오 지오코딩 파이프라인**으로 46,897건 좌표 보완 적재) — 의사결정은 [`docs/adr/`](./docs/adr) 참고
- 프론트엔드: **MVP 4화면(홈 지도·장소 상세·급해요·제보) Next.js 16(App Router)+TypeScript PWA** — 라이브 API에 서버 프록시로 연결. 설치·구조는 [`frontend/README.md`](./frontend/README.md)
- P2 착수: **휘발성 제보(reports) 풀스택 라이브** — 익명 `POST /reports`(11개 타입·타입별 TTL, 실시간 자리 여유/혼잡 `SEAT_OK`/`CROWDED` 포함 — 여름 실내 오래 버티기 신호, ADR-0005 부분채택) + 프론트 실전송·상세 최근제보. 레이트리밋 XFF 신뢰경계·OOM 하드닝(적대적 리뷰, TS-008)
- P3 착수: **간판 `survival_score` 구현** — 유효 제보를 최근성×신뢰도로 **SQL 뷰(PostGIS)에서 시공간 집계**(freshness/comfort/risk) + 순수 함수가 §5 가중치 조립·3색 등급(초록 지금 좋음/노랑 보통/회색 정보 부족). `/places`·`/places/{id}` 응답에 `survival` + 프론트 마커 3색·상태 배지([ADR-0007](./docs/adr/0007-survival-score-sql-signals-java-compose.md))

## 프론트엔드
🟢 **Live: [https://geuneul.vercel.app](https://geuneul.vercel.app)** — `frontend/` — Next.js 16 App Router · TypeScript · Tailwind v4 · TanStack Query · Kakao Maps · Serwist(PWA). 백엔드(`backend/`)와 독립 배포(Vercel). 브라우저는 동일 오리진 `/api/*` 서버 프록시만 호출해 ALB(http)·CORS 제약을 회피한다. 자세한 내용·환경변수는 [`frontend/README.md`](./frontend/README.md).

## API 맛보기 (P1)
```bash
# 숭실대 정문 반경 1km — 가까운 순 + 거리(m)
GET /places?lat=37.4963&lng=126.9575&radius=1000

# 지도 뷰포트(bounds) 마커 — 무더위쉼터 (전국 적재분)
GET /places?bounds=126.93,37.49,126.97,37.52&category=COOLING_SHELTER

# 가장 가까운 무더위쉼터 3곳 (kNN) — 서울 밖 어디서나 동작 (예: 부산)
GET /places/nearest?lat=35.2133&lng=129.0157&category=COOLING_SHELTER&limit=3
```
휘발성 제보 (P2 · 익명, 타입별 TTL로 자동 만료):
```bash
# 지금 상태 제보 (익명) — 분당 3·시간당 10 레이트리밋
POST /reports   {"placeId":1,"reportType":"COOL","comment":"에어컨 빵빵"}
# 장소의 유효(미만료) 제보 최신순
GET  /places/1/reports
```
`/places`(반경·bounds)·`/places/{id}` 응답엔 **`survival`**(지금 갈만함 점수 + 등급 GOOD/OKAY/UNKNOWN)이 함께 온다 — 마커 3색·상태 배지의 원천 (P3 · [ADR-0007](./docs/adr/0007-survival-score-sql-signals-java-compose.md)).
시나리오 추천 (P3 · survival_score에 시나리오 가중을 얹은 2단 랭킹 · [ADR-0008](./docs/adr/0008-recommendations-scenario-weighted-ranking.md)):
```bash
# 화장실 급함 / 잠깐 쉬어갈 곳 / 비 피할 곳 — 거리+실시간 상태로 "지금 갈만한 순"
GET /recommendations?lat=37.4963&lng=126.9575&scenario=restroom
# 각 결과 = 장소(survival 배지 포함) + matchScore(적합도) + reason(제보 요약)
```
공공데이터 적재(멱등 — 재실행해도 중복 없음):
```bash
./gradlew bootRun --args='--ingest.source=cooling_shelter --ingest.file=/path/무더위쉼터.csv --ingest.charset=MS949'
```
