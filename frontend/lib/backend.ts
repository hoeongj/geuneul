// 서버 전용. 브라우저는 동일 오리진 /api/* 만 호출하고, 이 프록시가 ALB 로 대신 요청한다.
// → ALB 가 http(TLS 없음)이고 CORS 미설정이어도 mixed-content/CORS 를 동시에 회피.
// GEUNEUL_API_BASE 는 서버 전용 env(NEXT_PUBLIC 아님)라 ALB 호스트가 클라이언트 번들에 노출되지 않는다.
import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";
import { SESSION_COOKIE } from "@/lib/auth";

const RAW_BASE = process.env.GEUNEUL_API_BASE ?? "";
const BASE = RAW_BASE.replace(/\/$/, "");

const TIMEOUT_MS = 10_000;

async function backendFetch(path: string, search: string): Promise<Response> {
  const url = `${BASE}${path}${search ? `?${search}` : ""}`;
  return fetch(url, {
    headers: { accept: "application/json" },
    signal: AbortSignal.timeout(TIMEOUT_MS),
    // 백엔드 Redis 가 캐시를 담당하므로 프록시는 항상 신선하게 통과(체감 상태의 freshness 우선).
    cache: "no-store",
  });
}

// 쓰기 프록시: JSON body를 그대로 중계하고, 원 클라이언트 IP(X-Forwarded-For)를 보존해
// 백엔드 레이트리밋(클라이언트별)이 프록시 IP로 뭉치지 않게 한다.
export async function proxyPost(path: string, request: Request): Promise<NextResponse> {
  if (!BASE) {
    return NextResponse.json(
      { error: "config", message: "GEUNEUL_API_BASE is not configured on the server." },
      { status: 500 },
    );
  }
  try {
    const body = await request.text();
    // 원 클라이언트 IP: Vercel이 세팅한 x-real-ip 우선, 없으면 XFF 최좌측.
    const xff = request.headers.get("x-forwarded-for") ?? "";
    const clientIp = request.headers.get("x-real-ip") ?? xff.split(",")[0].trim();
    // BFF↔백엔드 공유 시크릿(서버 전용). 설정 시 백엔드가 x-client-ip를 신뢰해 XFF 위조 우회를 차단(ProxyClientResolver).
    // 미설정이면 빈 헤더 → 백엔드는 기존 최좌측 XFF 동작(회귀 없음).
    const proxySecret = process.env.GEUNEUL_PROXY_SECRET ?? "";
    const res = await fetch(`${BASE}${path}`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        accept: "application/json",
        ...(xff ? { "x-forwarded-for": xff } : {}),
        ...(clientIp ? { "x-client-ip": clientIp } : {}),
        ...(proxySecret ? { "x-proxy-auth": proxySecret } : {}),
      },
      body,
      signal: AbortSignal.timeout(TIMEOUT_MS),
      cache: "no-store",
    });
    const resBody = await res.text();
    return new NextResponse(resBody, {
      status: res.status,
      headers: { "content-type": res.headers.get("content-type") ?? "application/json; charset=utf-8" },
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return NextResponse.json({ error: "upstream_unreachable", message }, { status: 502 });
  }
}

// 로그인 필요 쓰기 프록시(후기 작성 등): 세션 쿠키(httpOnly JWT)를 Bearer로 백엔드에 전달한다.
// 쿠키가 없으면 백엔드까지 가지 않고 즉시 401(불필요한 왕복 방지) — /api/me와 동일 판정 기준.
// 백엔드가 돌려주는 401(토큰 만료 등)·400(검증 실패)은 그대로 통과시켜 클라이언트가 구분할 수 있게 한다.
export async function proxyAuthedPost(path: string, request: NextRequest): Promise<NextResponse> {
  if (!BASE) {
    return NextResponse.json(
      { error: "config", message: "GEUNEUL_API_BASE is not configured on the server." },
      { status: 500 },
    );
  }
  const token = request.cookies.get(SESSION_COOKIE)?.value;
  if (!token) {
    return NextResponse.json({ error: "unauthenticated", message: "로그인이 필요해요." }, { status: 401 });
  }
  try {
    const body = await request.text();
    const res = await fetch(`${BASE}${path}`, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        accept: "application/json",
        authorization: `Bearer ${token}`,
      },
      body,
      signal: AbortSignal.timeout(TIMEOUT_MS),
      cache: "no-store",
    });
    const resBody = await res.text();
    return new NextResponse(resBody, {
      status: res.status,
      headers: { "content-type": res.headers.get("content-type") ?? "application/json; charset=utf-8" },
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return NextResponse.json({ error: "upstream_unreachable", message }, { status: 502 });
  }
}

// path 예: "/places", "/places/nearest", "/places/12"
export async function proxy(path: string, search: string): Promise<NextResponse> {
  if (!BASE) {
    return NextResponse.json(
      { error: "config", message: "GEUNEUL_API_BASE is not configured on the server." },
      { status: 500 },
    );
  }
  try {
    const res = await backendFetch(path, search);
    const body = await res.text();
    return new NextResponse(body, {
      status: res.status,
      headers: { "content-type": res.headers.get("content-type") ?? "application/json; charset=utf-8" },
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return NextResponse.json({ error: "upstream_unreachable", message }, { status: 502 });
  }
}

// ALB에서 JSON을 받아 파싱하는 서버측 헬퍼(BASE 미설정/4xx·5xx는 throw).
// 급해요의 카테고리별 nearest 팬아웃·병합은 이 헬퍼를 여러 번 호출하는 app/api/urgent/route.ts에 있다.
export async function backendJson<T>(path: string, search: string): Promise<T> {
  if (!BASE) throw new Error("GEUNEUL_API_BASE not configured");
  const res = await backendFetch(path, search);
  if (!res.ok) throw new Error(`upstream ${res.status}`);
  return (await res.json()) as T;
}
