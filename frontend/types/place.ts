// 라이브 백엔드(/places*)의 실측 응답 계약. 리스트/단건 모두 아래 필드만 반환하며,
// distanceM 은 radius/nearest 응답에서만 존재하고 bounds/단건에서는 null 이다.
export type Category =
  | "COOLING_SHELTER"
  | "TOILET"
  | "WATER"
  | "PARK"
  | "LIBRARY"
  | "CIVIC"
  | "UNDERGROUND"
  | "ETC";

// place_features 는 ERD엔 있으나 현재 API 응답엔 아직 없음(P2/P3에서 노출 예정) → optional.
export type FeatureType =
  | "air_conditioned"
  | "outlet"
  | "wifi"
  | "restroom"
  | "water"
  | "seating"
  | "no_eyes";

export interface Place {
  id: number;
  name: string;
  category: Category;
  categoryLabel: string;
  address: string;
  lat: number;
  lng: number;
  source: string;
  /** radius/nearest 에서만 채워짐. bounds/단건은 null. */
  distanceM: number | null;
  /** 백엔드 미노출(P2/P3). 있으면 상세 시설칩에 렌더. */
  features?: FeatureType[];
  /** 백엔드 미노출. 있으면 운영시간 렌더. */
  openHours?: string | null;
}

export interface MapBounds {
  west: number;
  south: number;
  east: number;
  north: number;
}

export type Scenario = "restroom" | "rest30" | "rain";

// ── 휘발성 제보 (백엔드 /reports 계약, 라이브 실측) ────────────────────────────
export type ReportTypeKey =
  | "SEAT_OK"
  | "CROWDED"
  | "COOL"
  | "HOT"
  | "BUG"
  | "ODOR"
  | "SMOKE"
  | "FLOOD"
  | "SLIPPERY"
  | "WATER_OK"
  | "RESTROOM_CLEAN";

export interface Report {
  id: number;
  placeId: number;
  reportType: ReportTypeKey;
  reportTypeLabel: string;
  comment: string | null;
  anonymous: boolean;
  createdAt: string; // ISO-8601 — 상대 시간은 클라이언트가 계산
  expiresAt: string;
}

export interface ReportCreatePayload {
  placeId: number;
  reportType: ReportTypeKey;
  comment?: string;
  anonymous?: boolean;
}
