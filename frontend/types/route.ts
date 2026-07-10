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

// 경로 주변 그늘/실내 피난처(F4) — 쉼터·도서관·지하상가 오버레이("더울 때 피할 곳").
export interface ShadeSpot {
  placeId: number;
  name: string;
  category: string; // enum name (COOLING_SHELTER | LIBRARY | UNDERGROUND)
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
  shadeSpots: ShadeSpot[]; // F4 — 경로 corridor 주변 피난처
}
