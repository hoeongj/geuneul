import type { Place, Survival, SurvivalGrade } from "@/types/place";

// survival_score 등급 → 표시 메타(마커 링·상태 점·라벨). 마커/리스트/상세가 공유하는 단일 원천.
// 색은 야외 시인성 우선(라이트 모드) 3색 세마포어: 초록/노랑/회색.
export const GRADE_META: Record<SurvivalGrade, { label: string; color: string }> = {
  GOOD: { label: "지금 좋음", color: "#2ba25f" }, // 초록 — 최근 제보 기준 갈만함
  OKAY: { label: "보통", color: "#e0a13a" }, // 노랑 — 제보는 있으나 리스크/애매
  UNKNOWN: { label: "정보 부족", color: "#9aa6a0" }, // 회색(--color-status) — 유효 제보 없음
};

// 응답에 survival 이 없으면(nearest/urgent 등 비스코어드 경로) UNKNOWN 으로 취급.
export function gradeOf(place: Pick<Place, "survival">): SurvivalGrade {
  return place.survival?.grade ?? "UNKNOWN";
}

// 상태 라벨: GOOD/OKAY 는 점수를 붙여 신뢰를 준다("지금 좋음 · 82"), UNKNOWN 은 라벨만.
export function statusLabel(survival: Survival | null | undefined): { label: string; color: string } {
  const grade = survival?.grade ?? "UNKNOWN";
  const meta = GRADE_META[grade];
  if (grade === "UNKNOWN") return { label: meta.label, color: meta.color };
  return { label: `${meta.label} · ${survival!.score}`, color: meta.color };
}
