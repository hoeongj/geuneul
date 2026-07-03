# 그늘 프론트엔드 (MVP 4화면)

여름 생존 지도의 모바일 웹(PWA). **홈 지도 · 장소 상세 · 지금 급해요 · 제보하기** 4화면을
라이브 백엔드(PostGIS 공간검색 API)에 연결한다. 디자인은 하이파이 핸드오프를 대상 스택으로 픽셀에 가깝게 재현.

## 스택 (2026 기준)

| 영역 | 선택 | 이유 |
|---|---|---|
| 프레임워크 | **Next.js 16 (App Router) + React 19 + TypeScript** | 브리프 지정. 공유 링크·SEO·PWA에 유리 |
| 스타일 | **Tailwind CSS v4** (`@theme` 토큰) | 디자인 토큰을 테마로 매핑, 픽셀 재현 |
| 데이터 패칭 | **TanStack Query v5** | `queryKey=bounds` 캐싱으로 뷰포트 과호출 방지, P2 mutation 대비 |
| 지도 | **react-kakao-maps-sdk** (`clusterer`) | `useKakaoLoader` + `MarkerClusterer`. 마커는 SVG data-URI |
| PWA | **Serwist** (`@serwist/next`) | next-pwa 후계(공식 문서 권장). 매니페스트·SW·오프라인 셸 |
| 아이콘 | 자체 `<Icon>` (프로토타입 `svgIcon` 재현) | 마커 data-URI와 UI가 동일 아이콘 시스템 공유 |
| 폰트 | Pretendard Variable self-host (`next/font/local`) | 오프라인 셸·CSP 안정성 |

> **빌드는 webpack 고정**(`next build --webpack`). Serwist가 webpack 플러그인 기반이라 Next 16 기본
> Turbopack 과 함께 쓸 수 없다(2026 기준 Serwist+Turbopack 은 실험 단계). 상세는 `../TROUBLESHOOTING.md`.

## 화면 ↔ API 매핑

| 화면 | 데이터 | 프록시 |
|---|---|---|
| 홈 지도 마커 | 뷰포트 `bounds` 조회 | `GET /api/places?bounds=…` |
| 바텀시트 리스트 | 현재 위치 `radius` 조회(distanceM 포함) | `GET /api/places?lat=&lng=&radius=` |
| 지금 급해요 | 시나리오별 `nearest`(kNN) 서버 팬아웃·병합 | `GET /api/urgent?scenario=&lat=&lng=` |
| 장소 상세 | 단건 | `GET /api/places/{id}` |

- `distanceM` 은 radius/nearest 응답에만 존재 → 있으면 `364m`, 없으면 거리 숨김(상세는 현재 위치 기준 직선거리로 근사).
- 도보 예상 = `max(1, round(distanceM / 67))`분.
- 마커 링은 회색(정보 부족) 고정 — survival_score 3색은 P3 슬롯만.

## 서버 프록시 (핵심 제약)

백엔드 ALB 는 `http`(TLS 없음)이고 CORS 미설정이라 **브라우저가 직접 호출하면 mixed-content/CORS 로 차단**된다.
→ 브라우저는 항상 **동일 오리진 `/api/*`** 만 호출하고, Next Route Handler(`app/api/**`)가 서버에서 ALB 로
프록시한다. 백엔드 주소는 **서버 전용 env `GEUNEUL_API_BASE`**(NEXT_PUBLIC 아님)라 클라이언트 번들에 노출되지 않는다.

## 환경변수 (`.env.local`, 커밋 금지)

```bash
GEUNEUL_API_BASE=http://<alb-host>            # 서버 전용. 브라우저 미노출.
NEXT_PUBLIC_KAKAO_MAP_JS_KEY=<js-key>         # Kakao "JavaScript 키"(REST 키와 다름)
```

- **Kakao JS 키**: 지오코딩용 REST 키와 다른 키. Kakao 콘솔 **앱 > 플랫폼 > Web** 에
  `http://localhost:3000`(+ 배포 도메인) 등록 필요. JS SDK 특성상 브라우저 노출이 정상이며,
  보안은 도메인 허용목록으로 건다.
- 키가 없으면 **지도는 placeholder** 로 뜨고 리스트·급해요 등 데이터는 정상 동작한다. 키를 넣으면 실지도가 뜬다.

## 개발

```bash
pnpm install
pnpm dev          # http://localhost:3000 (webpack)
pnpm typecheck    # tsc --noEmit
pnpm lint         # eslint
pnpm build        # 프로덕션 빌드(Serwist SW 번들)
```

## 구조

```
app/
  (shell)/          # 하단 3탭 셸 + 장소상세 오버레이 슬롯
    page.tsx        # ① 홈 지도
    urgent/         # ③ 지금 급해요
    report/         # ④ 제보하기(P2 프리뷰)
  api/              # 서버 프록시(places·nearest·[id]·urgent)
  manifest.ts       # PWA 매니페스트
  sw.ts             # Serwist 서비스워커
  ~offline/         # 오프라인 셸
components/  map · place · urgent · shell · ui
lib/         backend(서버) · api·queries(클라) · categories · geo · marker · kakao · context
```

## 범위 (핸드오프 준수)

MVP = 4화면 레이아웃·인터랙션 + 라이브 조회 API 연동. **P2/P3(후기·제보 POST·로그인·freshness·AI 요약·
survival_score 3색)은 자리만** 두고 구현하지 않는다.
