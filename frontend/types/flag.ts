// 신고/모더레이션(C1) — 백엔드 domain.flag DTO와 1:1.
export type FlagTargetType = "REPORT" | "REVIEW";
export type FlagReason = "SPAM" | "FALSE_INFO" | "OFFENSIVE" | "OTHER";
export type FlagStatus = "PENDING" | "RESOLVED" | "DISMISSED";

// POST /flags 요청(FlagCreateRequest — reporterId는 서버가 JWT에서 취함, 바디에 없음).
export interface FlagCreatePayload {
  targetType: FlagTargetType;
  targetId: number;
  reason: FlagReason;
  detail?: string;
}

// POST /flags 응답(FlagResponse).
export interface Flag {
  id: number;
  targetType: FlagTargetType;
  targetId: number;
  reason: FlagReason;
  detail: string | null;
  status: FlagStatus;
  createdAt: string;
  resolvedAt: string | null;
}

// GET /admin/flags 항목(FlagPendingItemResponse) — 신고 + 대상 요약.
export interface AdminFlagItem {
  id: number;
  targetType: FlagTargetType;
  targetId: number;
  reason: FlagReason;
  detail: string | null;
  status: FlagStatus;
  createdAt: string;
  reporterId: number;
  targetExists: boolean;
  targetSummary: string | null;
}

// GET /admin/flags 목록(FlagPendingListResponse, 페이지네이션).
export interface AdminFlagList {
  flags: AdminFlagItem[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
}

// 신고 사유 라벨(§6 중립 톤) — 라디오 선택지·큐 표시 공용.
export const FLAG_REASONS: { value: FlagReason; label: string }[] = [
  { value: "SPAM", label: "스팸/도배" },
  { value: "FALSE_INFO", label: "허위 정보" },
  { value: "OFFENSIVE", label: "불쾌·명예훼손" },
  { value: "OTHER", label: "기타" },
];

export const FLAG_REASON_LABEL: Record<FlagReason, string> = {
  SPAM: "스팸/도배",
  FALSE_INFO: "허위 정보",
  OFFENSIVE: "불쾌·명예훼손",
  OTHER: "기타",
};
