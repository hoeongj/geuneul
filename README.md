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
🟢 **Live** — [http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com](http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com/actuator/health) (AWS ECS Fargate + RDS PostGIS, `main` push 시 자동배포)

- W0 완료(2026-07-02): Spring Boot 4 + PostGIS/Flyway + Testcontainers CI + **AWS(ECS Fargate·RDS·Terraform·OIDC) 배포 파이프라인**
- P1 진행 중: **반경(ST_DWithin)/kNN(`<->`)/bounds 공간검색 API + 무더위쉼터 idempotent 인제스천** — 의사결정은 [`docs/adr/`](./docs/adr) 참고

## API 맛보기 (P1)
```bash
# 숭실대 정문 반경 1km — 가까운 순 + 거리(m)
GET /places?lat=37.4963&lng=126.9575&radius=1000

# 지도 뷰포트(bounds) 마커
GET /places?bounds=126.93,37.49,126.97,37.52&category=TOILET

# 가장 가까운 화장실 3곳 (kNN)
GET /places/nearest?lat=37.4963&lng=126.9575&category=TOILET&limit=3
```
공공데이터 적재(멱등 — 재실행해도 중복 없음):
```bash
./gradlew bootRun --args='--ingest.source=cooling_shelter --ingest.file=/path/무더위쉼터.csv --ingest.charset=MS949'
```
