// 로그인 사용자 — 백엔드 /me·/auth 응답(UserResponse)과 1:1.
export interface User {
  id: number;
  provider: "KAKAO" | "GOOGLE";
  nickname: string;
  email: string | null;
  profileImage: string | null;
  trustScore: number;
  role: "USER" | "ADMIN";
}
