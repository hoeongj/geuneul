# ADR-0004. 프론트는 동일 오리진 서버 프록시(BFF)로만 백엔드에 접근

- 상태: 승인 (2026-07-03)
- 관련: `frontend/app/api/**`(Route Handlers), `frontend/lib/backend.ts`, env `GEUNEUL_API_BASE`

## 문제(Context)

프론트(Vercel, https)가 백엔드 ALB에 붙어야 하는데 ALB는 **http(TLS 없음) + CORS 미설정**이다.
브라우저가 ALB를 직접 호출하면 ① https 페이지의 http 요청은 **mixed-content로 차단**되고
② 설령 통과해도 크로스오리진이라 **CORS 프리플라이트에서 차단**된다.
ACM 인증서 + 도메인 + 443 리스너(ALB HTTPS)는 도메인 구매·인프라 변경이 필요해 MVP 시점엔 과하다.

## 결정(Decision)

1. **브라우저는 동일 오리진 `/api/*`만 호출한다.** Next.js Route Handler(`app/api/**`)가
   서버에서 ALB로 대신 요청(프록시)한다 — 서버↔ALB 구간은 브라우저 보안 정책 밖이라 http여도 무방.
2. 백엔드 주소는 **서버 전용 env `GEUNEUL_API_BASE`**(`NEXT_PUBLIC` 아님)로만 주입 —
   ALB 호스트가 클라이언트 번들에 노출되지 않는다.
3. 프록시는 단순 중계를 넘어 **BFF 레이어**로 쓴다 — 예: `/api/urgent`는 시나리오의
   카테고리별 `nearest`(kNN)를 서버에서 팬아웃→중복 제거→거리순 병합(단일 category 파라미터 API의
   다중 카테고리 시나리오 대응). 클라이언트 왕복 N회를 서버 1회로 축약.
4. 백엔드에 CORS 설정을 **하지 않는다** — 필요 없어졌고, 공개 오리진 화이트리스트 관리 부담 제거.

## 검토한 대안(Alternatives)

| 대안 | 기각 이유 |
|---|---|
| ALB HTTPS(ACM+도메인+443) + CORS | 도메인 구매·인프라 변경 필요. MVP엔 과설계 — P4 백로그(공유 링크 신뢰도·직접 API 공개 시 재검토) |
| `NEXT_PUBLIC_API_BASE`로 브라우저 직접 호출 | mixed-content로 즉시 차단. CORS도 별도 필요. 호스트 노출 |
| next.config `rewrites`만 사용 | 단순 중계는 되지만 헤더 제어·팬아웃·검증 같은 BFF 로직 불가. Route Handler가 상위호환 |
| Vercel Edge Middleware 프록시 | Route Handler 대비 이점 없음(스트리밍·지역성 불필요), 디버깅만 복잡 |

## 결과(Consequences)

- mixed-content·CORS를 **한 구조로 동시 해소**, ALB 호스트 비노출, 급해요 팬아웃 등 BFF 로직 확보.
- 모든 데이터 요청이 Vercel 함수를 경유 — 호출량이 커지면 함수 비용·지연이 늘 수 있다(현 규모 무시 가능,
  백엔드 Redis 캐시와 TanStack Query 클라 캐시가 완충). ALB HTTPS 도입 시 프록시 제거는 env 교체 수준.
- 백엔드 입장에서 실제 클라이언트 IP는 XFF 헤더로만 전달된다 — 남용 방어(레이트리밋)는 이 헤더 기준.

## 근거(References)

- MDN Mixed Content — https 문서의 http 하위요청 차단
- Next.js Route Handlers (App Router) — 서버 전용 env·프록시 패턴 (2026 표준 BFF 관례)
- TROUBLESHOOTING TS-006/TS-007, WORKLOG 2026-07-03 항목
