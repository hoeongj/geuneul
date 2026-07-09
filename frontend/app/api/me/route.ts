import { NextRequest, NextResponse } from "next/server";
import { SESSION_COOKIE } from "@/lib/auth";

const TIMEOUT_MS = 10_000;

// GET /api/me — 세션 쿠키의 JWT를 Bearer로 백엔드 /me에 전달(httpOnly라 브라우저 JS는 토큰을 못 봄).
// 미로그인(쿠키 없음)이면 401 — 클라 useMe()는 이를 "로그아웃 상태"로 처리.
export async function GET(request: NextRequest) {
  const token = request.cookies.get(SESSION_COOKIE)?.value;
  if (!token) return NextResponse.json({ error: "unauthenticated" }, { status: 401 });

  const base = process.env.GEUNEUL_API_BASE?.replace(/\/$/, "");
  if (!base) return NextResponse.json({ error: "config" }, { status: 500 });

  try {
    const res = await fetch(`${base}/me`, {
      headers: { accept: "application/json", authorization: `Bearer ${token}` },
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    const body = await res.text();
    return new NextResponse(body, {
      status: res.status,
      headers: { "content-type": res.headers.get("content-type") ?? "application/json; charset=utf-8" },
    });
  } catch {
    return NextResponse.json({ error: "upstream" }, { status: 502 });
  }
}
