# 배포 (PaaS 경량 — push→자동배포)

> 백엔드 **Railway** · DB **Supabase(PostGIS)** · 프론트 **Vercel**.
> 원칙: mp가 이미 k3s/ArgoCD GitOps를 증명 → 그늘은 복제하지 않고 PaaS로 경량 배포(다른 패러다임=breadth, 에너지는 지리공간 백엔드에). 진짜 새 DevOps(HPA/오토스케일링)는 P4에서 k6 부하테스트와 함께.
>
> ⚠️ 모든 비밀(DB 비번·API 키)은 **각 플랫폼의 환경변수**로만. 레포에 커밋 금지(규칙 D).

## 1. DB — Supabase (PostGIS)
1. https://supabase.com → New project. Region: **Northeast Asia (Seoul)** 권장. DB 비밀번호 설정.
2. Dashboard → **Database → Extensions** 에서 `postgis` **enable**. (Flyway `V1__enable_postgis.sql`의 `CREATE EXTENSION IF NOT EXISTS postgis`도 멱등하게 처리하지만, 미리 켜두면 안전.)
3. **Connect** → **Session pooler** 연결정보를 복사(포트 5432, 호스트 `aws-0-<region>.pooler.supabase.com`).
   - ⚠️ Supabase 직접연결(db.\<ref\>.supabase.co)은 IPv6 전용 → Railway에서 안 붙을 수 있음. **Session pooler(IPv4)** 를 쓸 것.
4. 아래 값을 메모: `HOST`, `PORT(5432)`, `DB(postgres)`, `USER(postgres.\<ref\>)`, `PASSWORD`.

## 2. 백엔드 — Railway
1. https://railway.app → **New Project → Deploy from GitHub repo** → `hoeongj/geuneul` 선택.
2. 서비스 **Settings → Root Directory = `backend`** (모노레포라 필수). `backend/Dockerfile` 자동 감지.
3. **Variables** 에 환경변수 설정 (Supabase 값 매핑):
   ```
   DB_HOST=aws-0-<region>.pooler.supabase.com
   DB_PORT=5432
   DB_NAME=postgres
   DB_USERNAME=postgres.<ref>
   DB_PASSWORD=<supabase-db-password>
   ```
4. Redis: 프로젝트에 **+ New → Database → Redis** 추가 → 생성된 `REDIS_HOST`/`REDIS_PORT`(+비번 있으면 `REDIS_PASSWORD`)를 백엔드 서비스 Variables에 연결. (내부 네트워크라 TLS 불필요 → `REDIS_SSL=false`.)
5. Deploy. **Settings → Networking → Generate Domain** 으로 공개 URL 발급.
6. 확인: `https://<앱>.up.railway.app/actuator/health` → `{"status":"UP"}`, Swagger `/swagger-ui.html`.
7. **자동배포:** 기본으로 `main` push마다 재빌드·재배포됨(별도 설정 불필요).

## 3. 프론트 — Vercel (프론트 생기면)
1. https://vercel.com → Import `hoeongj/geuneul` → Root Directory = `frontend`.
2. env: `NEXT_PUBLIC_KAKAO_MAP_KEY`, `NEXT_PUBLIC_API_BASE=https://<railway-domain>`.
3. push→자동배포.

## 확인 체크리스트
- [ ] Supabase postgis enable + Session pooler 연결정보 확보
- [ ] Railway Root=backend, DB/Redis 환경변수, 도메인 발급
- [ ] `/actuator/health` UP (Flyway 마이그레이션 성공 = PostGIS·GiST 생성됨)
- [ ] `main` push 시 자동 재배포 동작
