// 후기 커뮤니티(2차·살, docs/SPEC.md §8) — 댓글 + 리액션. 백엔드 /reviews/{id}/comments · /reactions.
// survival_score(간판)와 완전 분리. 프론트도 최소 표면만 연다(§0-9 — 커뮤니티가 주인공이 되지 않게).
export interface ReviewComment {
  id: number;
  userId: number;
  nickname: string;
  profileImage: string | null;
  comment: string;
  createdAt: string; // ISO-8601
}

// 리액션 대상/종류 — 현재 UI는 후기(REVIEW) + 유용해요(HELPFUL)만 노출한다.
export type ReactionTarget = "REVIEW" | "REPORT";
export type ReactionType = "HELPFUL";

export interface ReactionState {
  reacted: boolean;
  count: number;
}
