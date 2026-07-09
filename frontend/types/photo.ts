// 사진 업로드 presign — 백엔드 POST /photos/presign 계약(PhotoPresignResponse). 제보/후기 공용.
export type PhotoPurpose = "report" | "review";

export interface PhotoPresignResult {
  uploadUrl: string;
  objectUrl: string;
  key: string;
  expiresAt: string;
}

// 백엔드 PhotoService와 동일 상한 — 클라이언트에서 먼저 걸러 왕복 없이 빠른 피드백을 준다.
export const MAX_PHOTO_BYTES = 8 * 1024 * 1024;
export const ALLOWED_PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;
