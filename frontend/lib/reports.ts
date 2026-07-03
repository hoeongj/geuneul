import type { ReportTypeKey } from "@/types/place";

// 제보 타입 ↔ 이모지/라벨. 라벨은 API의 reportTypeLabel을 우선 쓰되(서버가 진실원천),
// 제보 그리드처럼 응답이 없는 자리는 여기 fallback을 쓴다. 백엔드 ReportType과 동기.
export const REPORT_META: Record<ReportTypeKey, { emoji: string; label: string }> = {
  COOL: { emoji: "🧊", label: "시원해요" },
  HOT: { emoji: "🥵", label: "더워요" },
  BUG: { emoji: "🐛", label: "벌레 많아요" },
  ODOR: { emoji: "🤢", label: "악취 나요" },
  SMOKE: { emoji: "🚬", label: "담배 냄새" },
  FLOOD: { emoji: "🌊", label: "침수됐어요" },
  SLIPPERY: { emoji: "🧊", label: "미끄러워요" },
  WATER_OK: { emoji: "💧", label: "물 있어요" },
  RESTROOM_CLEAN: { emoji: "🚻", label: "화장실 깨끗" },
};

// 제보하기 그리드에 노출하는 6종 (디자인 스펙 — 나머지 타입은 조회 표시만)
export const REPORT_GRID: ReportTypeKey[] = ["COOL", "HOT", "BUG", "RESTROOM_CLEAN", "WATER_OK", "FLOOD"];

/** "방금 전 / n분 전 / n시간 전 / n일 전" — 서버 시각(ISO) 기준 상대 표기. */
export function formatRelativeTime(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diffMs / 60_000);
  if (minutes < 1) return "방금 전";
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  return `${Math.floor(hours / 24)}일 전`;
}
