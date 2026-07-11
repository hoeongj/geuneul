// 경유지 경로(B2/F3 화장실·C4 그늘) — 백엔드 GET /routes/{toilet,shade}, 동일 스키마. mode=straight(직선 MVP)|road(외부 API).
export interface RouteStop {
  lat: number;
  lng: number;
  placeId: number | null; // 경유지만 non-null
  name: string | null;
  category: string | null; // 경유지 카테고리 enum name(미니맵 아이콘 구분용, C4). 출발/도착은 null.
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

// 경유지 경로 응답(화장실·그늘 공용). 이전 이름 ToiletRoute → 시나리오 확장(C4)으로 RouteResult로 일반화.
export interface RouteResult {
  origin: RouteStop;
  waypoint: RouteStop | null; // 경유지(없으면 null — 직선/도로 폴백)
  destination: RouteStop;
  polyline: LatLng[];
  mode: "straight" | "road";
  directDistanceM: number;
  routeDistanceM: number;
  shadeSpots: ShadeSpot[]; // F4 — 경로 corridor 주변 피난처
}
