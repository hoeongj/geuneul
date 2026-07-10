import type { Category } from "@/types/place";

// 관심 장소(bookmark, A7) — 백엔드 /bookmarks · /me/bookmarks. 로그인 유저 개인화(살).
export interface Bookmark {
  placeId: number;
  name: string;
  category: Category;
  categoryLabel: string;
  address: string;
  lat: number;
  lng: number;
  memo: string | null;
  createdAt: string; // ISO-8601
}

export interface BookmarkToggle {
  placeId: number;
  bookmarked: boolean;
}
