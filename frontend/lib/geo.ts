import type { MapBounds } from "@/types/place";

// geolocation 거부/실패 시 폴백 센터 = 서울 동작구(상도·노량진) — 필드테스트 거점.
export const FALLBACK_CENTER = { lat: 37.5024, lng: 126.9438 } as const;

// 반경(m). 백엔드 계약은 기본 800·최대 5000m이며, 프론트는 기본/넓히기 2단만 쓴다.
export const DEFAULT_RADIUS = 800;
export const WIDENED_RADIUS = 1500;

// 거리 표기: >=1km 는 소수1자리 km, 아니면 m. (프로토타입 fmtDist 와 동일)
export function formatDistance(m: number | null | undefined): string | null {
  if (m == null) return null;
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${Math.round(m)}m`;
}

// 도보 예상: round(distanceM / 67)분, 최소 1분. (README/프로토타입 규칙)
export function walkMinutes(m: number | null | undefined): number | null {
  if (m == null) return null;
  return Math.max(1, Math.round(m / 67));
}

// distanceM 이 없을 때(상세를 마커/bounds에서 열었을 때) 현재 위치 기준 직선거리 근사.
export function haversineMeters(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const R = 6371000;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

// 카카오 LatLngBounds → 백엔드 bounds 파라미터(west,south,east,north).
export function boundsParam(b: MapBounds): string {
  return `${b.west},${b.south},${b.east},${b.north}`;
}
