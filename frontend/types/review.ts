// 영구 후기 — 백엔드 /reviews · /places/{id}/reviews 계약(ReviewResponse/ReviewListResponse).
// survival_score(휘발성 제보)와 분리된 장소 평판(docs/SPEC.md §1/§5). 조회는 공개, 작성은 로그인 필요.
export interface Review {
  id: number;
  placeId: number;
  authorId: number; // 닉네임 탭 시 작성자 공개 프로필 이동(N7)
  authorNickname: string;
  authorProfileImage: string | null;
  rating: number; // 1~5
  comment: string | null;
  photos: string[];
  createdAt: string; // ISO-8601 — 상대 시간은 클라이언트가 계산
  updatedAt: string; // 재작성(upsert) 시 갱신
}

export interface ReviewListResponse {
  reviews: Review[];
  page: number;
  size: number;
  totalElements: number;
  hasNext: boolean;
}

export interface ReviewCreatePayload {
  placeId: number;
  rating: number;
  comment?: string;
  photos?: string[];
}
