# 그늘 (Geuneul)

> 여름 생존 지도 — 폭염·장마·벌레·화장실·식수·냉방·콘센트까지, 오늘 밖에서 살아남기 위한 생활 생존 지도.
> "지금 쉬어갈 그늘을 찾아드립니다."

지도에 위치만 찍지 않고 **"지금 상태"(시원함/자리/콘센트/벌레/침수)**를 최근 제보 기준으로 보여준다.
핵심 = `survival_score`(지금 갈만함 점수) + 최근성 기반 실시간 체감 정보.

- 첫 출시: 숭실대·상도·노량진·서울
- 스택: Next.js(PWA) · Spring Boot 4/Java 21 · PostgreSQL+PostGIS(Hibernate Spatial) · Redis · 카카오/구글 OAuth · Kakao Maps · Claude API
- 데이터: 무더위쉼터/공중화장실 표준데이터/서울시 공원음수대/기상청 + 유저 제보

## 시작하기
전체 목표·범위·ERD·API·로드맵은 **[`CLAUDE.md`](./CLAUDE.md)** 참고 (새 세션이 자동으로 읽음).

## 상태
🟢 **Live** — [http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com](http://geuneul-alb-1266310270.ap-northeast-2.elb.amazonaws.com/actuator/health) (AWS ECS Fargate + RDS PostGIS, `main` push 시 자동배포)

- W0 완료(2026-07-02): Spring Boot 4 + PostGIS/Flyway + Testcontainers CI + **AWS(ECS Fargate·RDS·Terraform·OIDC) 배포 파이프라인**
- 다음: P1 지리 코어 — 공공데이터 idempotent 인제스천 + 반경/kNN 검색 API
