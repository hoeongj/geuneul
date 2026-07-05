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

// survival_score(백엔드 §5·ADR-0007). 등급으로 마커 색을 칠한다:
// GOOD=초록(지금 좋음) / OKAY=노랑(보통) / UNKNOWN=회색(정보 부족).
export type SurvivalGrade = "GOOD" | "OKAY" | "UNKNOWN";

export interface Survival {
  score: number; // 0~100
  grade: SurvivalGrade;
  /** 거리 점수 0~1. 반경 검색에서만 non-null(bounds/단건은 null). */
  distanceScore: number | null;
  comfortScore: number;
  freshnessScore: number;
  riskScore: number;
  reportCount: number;
}

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
  /** 스코어드 검색(반경/bounds/단건)에서만 채워짐. nearest/urgent 경로는 없음(→ UNKNOWN 취급). */
  survival?: Survival | null;
  /** 백엔드 미노출(P2/P3). 있으면 상세 시설칩에 렌더. */
  features?: FeatureType[];
  /** 백엔드 미노출. 있으면 운영시간 렌더. */
  openHours?: string | null;
  /** 추천(/recommendations)에서만 채워짐 — 시나리오 적합도(0~100, 이 목록 정렬 기준). */
  matchScore?: number;
  /** 추천(/recommendations)에서만 채워짐 — 실시간 제보 근거 한 줄("최근 좋은 제보 2건"). */
  reason?: string;
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
