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
  | "CAFE"
  | "STUDY_CAFE"
  | "ETC";

// place_features 의 원본 feature_type(아이콘 매핑용). noise_level 은 등급형(ADR-0005 §④).
export type FeatureType =
  | "air_conditioned"
  | "outlet"
  | "wifi"
  | "restroom"
  | "water"
  | "seating"
  | "no_eyes"
  | "study_ok"
  | "quiet"
  | "noise_level";

// 상세(GET /places/{id})의 등급화된 시설 속성(ADR-0005 §④). label 은 백엔드가 등급을 반영해 만든 표시 문구,
// polarity 는 쾌적 방향(칩 색). present=false 는 백엔드가 애초에 제외하므로 여기 오는 건 모두 표시 대상.
export interface PlaceFeature {
  type: FeatureType;
  value: string | null;
  level: string;
  label: string;
  polarity: "POSITIVE" | "NEGATIVE" | "NEUTRAL";
  source: string | null;
  confidence: number | null;
}

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
  /** 상세(GET /places/{id})에서만 채워짐 — 등급화된 시설 속성(ADR-0005 §④). 목록/마커는 없음. */
  features?: PlaceFeature[];
  /** 상세에서만 채워짐 — 최근 제보 기준 AI 한 줄 요약(곁다리, ADR-0010). 없으면 null. */
  aiSummary?: string | null;
  /** 백엔드 미노출. 있으면 운영시간 렌더. */
  openHours?: string | null;
  /** 추천(/recommendations)에서만 채워짐 — 실시간 제보 근거 한 줄("최근 좋은 제보 2건").
   * (정렬 기준인 matchScore는 백엔드가 순서를 확정하므로 프론트는 순서만 신뢰하고 값은 안 싣는다.) */
  reason?: string;
}

export interface MapBounds {
  west: number;
  south: number;
  east: number;
  north: number;
}

export type Scenario = "restroom" | "rest30" | "rain" | "focus" | "longstay";

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
  /** P2 사진 presign(POST /photos/presign) 결과 objectUrl — 없으면 null. */
  photoUrl: string | null;
  anonymous: boolean;
  /** GPS 방문 인증(ADR-0005 §④) — 제보자가 장소 100m 이내에서 올림. "방문 인증" 배지. */
  verified: boolean;
  createdAt: string; // ISO-8601 — 상대 시간은 클라이언트가 계산
  expiresAt: string;
}

export interface ReportCreatePayload {
  placeId: number;
  reportType: ReportTypeKey;
  comment?: string;
  photoUrl?: string;
  anonymous?: boolean;
  /** 제보자 현재 좌표(선택) — 장소 100m 이내면 백엔드가 verified 처리(GPS 방문 인증). */
  lat?: number;
  lng?: number;
}
