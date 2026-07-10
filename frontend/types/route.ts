// 화장실 포함 경로(B2, ADR-0019) — 백엔드 GET /routes/toilet. mode=straight(직선 MVP)|road(외부 API 후속).
export interface RouteStop {
  lat: number;
  lng: number;
  placeId: number | null; // 경유 화장실만 non-null
  name: string | null;
}

export interface LatLng {
  lat: number;
  lng: number;
}

export interface ToiletRoute {
  origin: RouteStop;
  waypoint: RouteStop | null; // 경유 화장실(없으면 null)
  destination: RouteStop;
  polyline: LatLng[];
  mode: "straight" | "road";
  directDistanceM: number;
  routeDistanceM: number;
}
