// 그늘 P4 간판 부하테스트 — PostGIS 반경/kNN/bounds/추천 공간쿼리 (docs/SPEC.md §7 Test/Ops, §10 P4).
//
// 목적: "PostGIS 대용량 지리검색"(간판)이 GiST 인덱스로 실제로 빠른지 부하로 실증한다.
// 로컬 docker-compose(PostGIS) + 합성 시드(perf/seed/seed_synthetic_places.sql, 30만 places)에만 건다.
// 프로덕션 ALB 고부하 금지(docs/SPEC.md 지시) — BASE_URL 기본값은 로컬.
//
// 실행:
//   docker compose up -d && (백엔드) ./gradlew bootRun
//   psql ... -f perf/seed/seed_synthetic_places.sql   # 30만건 시드
//   k6 run perf/k6/spatial_load.js
//   스모크:  k6 run --vus 1 --duration 5s perf/k6/spatial_load.js
//   원격(주의): BASE_URL=http://host:8080 k6 run perf/k6/spatial_load.js
//
// 좌표: 시드 분포(수도권 70% + 전국 30%)를 반영해 랜덤화한다 — 캐시·핫스팟 없이 실사용 분포로 인덱스를 태운다.

import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 엔드포인트별 지연 분리 관측(전체 http_req_duration은 섞이므로 시나리오별 Trend를 따로 둔다).
const radiusDur = new Trend('geo_radius_duration', true);
const nearestDur = new Trend('geo_nearest_duration', true);
const boundsDur = new Trend('geo_bounds_duration', true);
const recoDur = new Trend('geo_reco_duration', true);

// 시드와 동일한 분포로 중심 좌표를 뽑는다(수도권 70% / 전국 30%).
function randomCenter() {
  if (Math.random() < 0.7) {
    return { lat: 37.2 + Math.random() * 0.55, lng: 126.6 + Math.random() * 0.7 };   // 수도권
  }
  return { lat: 33.1 + Math.random() * 5.5, lng: 124.6 + Math.random() * 6.4 };       // 전국
}

const CATEGORIES = [null, 'TOILET', 'WATER', 'LIBRARY', 'COOLING_SHELTER'];
const SCENARIOS = ['rest30', 'restroom', 'rain'];

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

// VU 목표는 환경에 맞춰 조절한다. 기본값 5는 로컬 colima(2 vCPU, amd64-emulated PostGIS)가
// 인덱스 서빙 지연을 유지하며 버티는 상한이다 — 이 환경은 amd64 PostGIS를 qemu로 에뮬레이트하므로
// CPU가 병목이라 ~10 RPS에서 처리량이 포화한다(VU를 올리면 처리량이 아니라 지연만 증가 — 실측 4/8/12 VU
// 모두 ~10 RPS). 절대 지연은 에뮬레이션으로 부풀려져 있고(네이티브 RDS는 훨씬 빠름), 임계값은 "이 환경에서
// 인덱스가 실제로 서빙 중"임을 지키는 회귀 가드로 잡았다(GiST 인덱스가 빠지면 이 관대한 임계도 뚫린다).
// 처리량 상한·포화 곡선의 근거와 EXPLAIN 인덱스 증빙은 docs/adr/0012, perf/explain/ 참고.
const PEAK_VUS = Number(__ENV.PEAK_VUS || 4);

export const options = {
  scenarios: {
    // 램프업: 0→PEAK 20초 상승, PEAK 40초 유지, 0으로 10초 하강.
    spatial: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: PEAK_VUS },
        { duration: '40s', target: PEAK_VUS },
        { duration: '10s', target: 0 },
      ],
      gracefulStop: '5s',
    },
  },
  thresholds: {
    // 공간쿼리(간판) p95/p99 임계 — 로컬 emulated PostGIS + PEAK_VUS=5 기준(에뮬레이션 여유 포함).
    'http_req_failed': ['rate<0.01'],                       // 실패율 1% 미만
    'geo_nearest_duration': ['p(95)<500', 'p(99)<900'],     // 순수 KNN <-> — 가장 빠르고 포화에 가장 강함
    'geo_radius_duration': ['p(95)<2000', 'p(99)<3200'],    // ST_DWithin geography + 뷰 조인
    'geo_bounds_duration': ['p(95)<900', 'p(99)<1800'],     // geometry && 박스 + 뷰 조인
    'geo_reco_duration': ['p(95)<2000', 'p(99)<3200'],      // 2단 검색(공간 선필터 → 시나리오 재랭킹)
  },
};

export default function () {
  const c = randomCenter();
  const cat = pick(CATEGORIES);
  const catParam = cat ? `&category=${cat}` : '';

  // 1) 반경 검색 (ST_DWithin geography, GiST) — 홈 지도 마커 조회의 주 경로.
  const radius = 400 + Math.floor(Math.random() * 1600); // 400~2000m
  let res = http.get(`${BASE_URL}/places?lat=${c.lat}&lng=${c.lng}&radius=${radius}${catParam}&limit=100`,
    { tags: { name: 'radius' } });
  check(res, { 'radius 200': (r) => r.status === 200 });
  radiusDur.add(res.timings.duration);

  // 2) kNN 최근접 (<-> KNN, GiST) — "화장실 급할 때" 팬아웃 기반 쿼리.
  res = http.get(`${BASE_URL}/places/nearest?lat=${c.lat}&lng=${c.lng}${catParam}&limit=5`,
    { tags: { name: 'nearest' } });
  check(res, { 'nearest 200': (r) => r.status === 200 });
  nearestDur.add(res.timings.duration);

  // 3) bounds (geometry && 박스, GiST) — 지도 뷰포트 마커. 중심 주변 ~3km 박스.
  const d = 0.015 + Math.random() * 0.02; // ~1.7~3.9km
  const bounds = `${(c.lng - d).toFixed(5)},${(c.lat - d).toFixed(5)},${(c.lng + d).toFixed(5)},${(c.lat + d).toFixed(5)}`;
  res = http.get(`${BASE_URL}/places?bounds=${bounds}${catParam}&limit=100`, { tags: { name: 'bounds' } });
  check(res, { 'bounds 200': (r) => r.status === 200 });
  boundsDur.add(res.timings.duration);

  // 4) 추천 (survival_score 시나리오 가중 + 뷰 조인) — 2단 검색 경로.
  res = http.get(`${BASE_URL}/recommendations?lat=${c.lat}&lng=${c.lng}&scenario=${pick(SCENARIOS)}&limit=5`,
    { tags: { name: 'reco' } });
  check(res, { 'reco 200': (r) => r.status === 200 });
  recoDur.add(res.timings.duration);
}
