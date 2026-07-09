import { NextRequest, NextResponse } from "next/server";
import { callbackUri, isLocalOrigin, isProvider, originOf, SESSION_COOKIE, STATE_COOKIE } from "@/lib/auth";

const TIMEOUT_MS = 10_000;
const SESSION_MAX_AGE = 7 * 24 * 60 * 60; // 7일 — 백엔드 JWT 만료와 정렬

// GET /api/auth/{provider}/callback — 제공자가 code와 함께 되돌려보내는 지점.
// state 검증 → 백엔드 /auth/{provider} 로 code 교환 → JWT를 httpOnly 쿠키로 심고 /mypage 로 이동.
export async function GET(request: NextRequest, { params }: { params: Promise<{ provider: string }> }) {
  const { provider } = await params;
  const origin = originOf(request);
  const fail = (reason: string) => NextResponse.redirect(`${origin}/mypage?error=${reason}`);

  if (!isProvider(provider)) return fail("provider");

  const url = new URL(request.url);
  const code = url.searchParams.get("code");
  const state = url.searchParams.get("state");
  const savedState = request.cookies.get(STATE_COOKIE)?.value;
  if (!code || !state || !savedState || state !== savedState) {
    return fail("state"); // CSRF 방어: 왕복 state 불일치/누락
  }

  const base = process.env.GEUNEUL_API_BASE?.replace(/\/$/, "");
  if (!base) return fail("config");
  const proxySecret = process.env.GEUNEUL_PROXY_SECRET ?? "";

  let token: string | undefined;
  try {
    const res = await fetch(`${base}/auth/${provider}`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        accept: "application/json",
        ...(proxySecret ? { "x-proxy-auth": proxySecret } : {}),
      },
      body: JSON.stringify({ code, redirectUri: callbackUri(request, provider) }),
      cache: "no-store",
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
    if (!res.ok) return fail("login");
    token = (await res.json())?.token;
  } catch {
    return fail("upstream");
  }
  if (!token) return fail("login");

  const res = NextResponse.redirect(`${origin}/mypage`);
  res.cookies.set(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: !isLocalOrigin(request),
    sameSite: "lax",
    path: "/",
    maxAge: SESSION_MAX_AGE,
  });
  res.cookies.delete(STATE_COOKIE);
  return res;
}
