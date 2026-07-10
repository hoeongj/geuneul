// 내 글 관리(N6) — 백엔드 /me/reviews·/me/comments·/me/reactions 계약. 로그인 필요.
// 커뮤니티는 "살"이라 개인 활동 모아보기일 뿐, 공개 피드가 아니다(§0-9).
export interface MyReview {
  reviewId: number;
  placeId: number;
  placeName: string;
  rating: number;
  comment: string | null;
  createdAt: string;
}

export interface MyComment {
  commentId: number;
  reviewId: number;
  placeId: number;
  placeName: string;
  comment: string;
  createdAt: string;
}

export interface MyReaction {
  reactionId: number;
  reviewId: number;
  placeId: number;
  placeName: string;
  reviewComment: string | null;
  createdAt: string;
}
