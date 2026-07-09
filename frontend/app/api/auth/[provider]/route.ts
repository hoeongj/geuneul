import { NextResponse } from "next/server";
import { authorizeUrl, callbackUri, isLocalOrigin, isProvider, STATE_COOKIE } from "@/lib/auth";

// GET /api/auth/{kakao|google} — 로그인 시작. CSRF state를 httpOnly 쿠키에 심고 제공자 authorize로 302.
export async function GET(request: Request, { params }: { params: Promise<{ provider: string }> }) {
  const { provider } = await params;
  if (!isProvider(provider)) {
    return NextResponse.json({ error: "unknown provider" }, { status: 404 });
  }

  const state = crypto.randomUUID();
  const url = authorizeUrl(provider, callbackUri(request, provider), state);
  if (!url) {
    return NextResponse.json({ error: "oauth not configured" }, { status: 500 });
  }

  const res = NextResponse.redirect(url);
  res.cookies.set(STATE_COOKIE, state, {
    httpOnly: true,
    secure: !isLocalOrigin(request),
    sameSite: "lax", // 제공자에서 되돌아오는 top-level 내비게이션에 쿠키가 실려야 함
    path: "/",
    maxAge: 600, // 10분 — 로그인 왕복 동안만
  });
  return res;
}
