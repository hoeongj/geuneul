import { NextResponse } from "next/server";
import { SESSION_COOKIE } from "@/lib/auth";

// POST /api/auth/logout — 세션 쿠키 삭제. 서버 상태가 없어 쿠키 제거만으로 로그아웃(무상태 JWT).
export async function POST() {
  const res = NextResponse.json({ ok: true });
  res.cookies.delete(SESSION_COOKIE);
  return res;
}
