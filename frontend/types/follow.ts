// 커먼스 세이프 팔로우(N7) — 백엔드 /users/{id}·/users/{id}/follow·/me/following 계약.
// §0-9: 팔로워 "수"만 공개(목록 없음), 팔로잉은 나만, 피드·맞팔 없음.
import type { MyReview } from "@/types/activity";

export interface UserProfile {
  id: number;
  nickname: string;
  profileImage: string | null;
  trustScore: number;
  followerCount: number; // 공개 카운트(목록은 비공개)
  following: boolean; // 내가 이 작성자를 팔로우 중인지(비로그인이면 false)
  reviews: MyReview[]; // 이 작성자의 공개 후기(N6 쿼리 재사용 shape)
}

export interface FollowResult {
  following: boolean;
  followerCount: number;
}

// 내 팔로잉 1건("나만" 봄) — 재방문용.
export interface Following {
  userId: number;
  nickname: string;
  profileImage: string | null;
  trustScore: number;
  followedAt: string;
}
