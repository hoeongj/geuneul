import type { NextRequest } from "next/server";
import { proxyAuthed } from "@/lib/backend";

// GET /api/notifications/rules → 내 알림 규칙 목록 (로그인 필요)
export function GET(request: NextRequest) {
  return proxyAuthed("GET", "/notifications/rules", request);
}

// POST /api/notifications/rules → 알림 규칙 생성 (로그인 필요)
export function POST(request: NextRequest) {
  return proxyAuthed("POST", "/notifications/rules", request);
}
