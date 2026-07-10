// 시간대별 혼잡 파생(자체 popular-times) 응답 계약 — 백엔드 GET /places/{id}/popular-times
// (ADR-0005 §④). dow/hour는 KST 기준. level은 CROWDED/SEAT_OK 상대비율로 유도된 등급.
export type CongestionLevel = "BUSY" | "MODERATE" | "QUIET" | "UNKNOWN";

export interface PopularTimesSlot {
  dow: number; // 0=일 ~ 6=토 (KST)
  hour: number; // 0~23 (KST)
  sampleCount: number;
  crowdedCount: number;
  seatOkCount: number;
  level: CongestionLevel;
}
