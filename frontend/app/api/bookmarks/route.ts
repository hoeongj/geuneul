import type { NextRequest } from "next/server";
import { proxyAuthed } from "@/lib/backend";

// POST /api/bookmarks → ALB /bookmarks (관심 장소 저장, 로그인 필요, 멱등)
export function POST(request: NextRequest) {
  return proxyAuthed("POST", "/bookmarks", request);
}
