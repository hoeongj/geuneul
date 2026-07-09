// 소셜 로그인 서버측 유틸(BFF). 브라우저 번들에 들어가지 않는 서버 전용 모듈.
// 흐름: 클라 → GET /api/auth/{provider}(authorize 리다이렉트) → 제공자 → GET /api/auth/{provider}/callback
//       → 백엔드 /auth/{provider} 로 code 교환 → JWT를 httpOnly 쿠키로.

export const PROVIDERS = ["kakao", "google"] as const;
export type Provider = (typeof PROVIDERS)[number];

/** JWT 세션 쿠키(httpOnly). /api/me·인증 프록시가 읽어 Bearer로 백엔드에 전달. */
export const SESSION_COOKIE = "geuneul_session";
/** OAuth CSRF state 쿠키(httpOnly, 단명). authorize→callback 왕복 검증용. */
export const STATE_COOKIE = "geuneul_oauth_state";

export function isProvider(value: string): value is Provider {
  return (PROVIDERS as readonly string[]).includes(value);
}

/** 요청의 공개 오리진(프로토콜+호스트). Vercel(x-forwarded-proto)·로컬 모두 처리. */
export function originOf(request: Request): string {
  const host = request.headers.get("host") ?? "localhost:3000";
  const proto = request.headers.get("x-forwarded-proto") ?? (host.startsWith("localhost") ? "http" : "https");
  return `${proto}://${host}`;
}

export function isLocalOrigin(request: Request): boolean {
  return originOf(request).startsWith("http://localhost");
}

/** 콜백 URL — 제공자 콘솔에 등록한 값과 정확히 일치해야 한다(토큰 교환 시 검증). */
export function callbackUri(request: Request, provider: Provider): string {
  return `${originOf(request)}/api/auth/${provider}/callback`;
}

/**
 * 제공자 authorize URL. client_id는 서버 전용 env(브라우저 노출 안 함).
 * 시크릿은 여기서 안 쓴다 — 토큰 교환(client_secret)은 백엔드가 수행.
 * 필요한 env가 없으면 null(호출부가 설정 오류로 처리).
 */
export function authorizeUrl(provider: Provider, redirectUri: string, state: string): string | null {
  if (provider === "kakao") {
    const clientId = process.env.KAKAO_REST_API_KEY;
    if (!clientId) return null;
    const params = new URLSearchParams({
      client_id: clientId,
      redirect_uri: redirectUri,
      response_type: "code",
      scope: "profile_nickname",
      state,
    });
    return `https://kauth.kakao.com/oauth/authorize?${params}`;
  }
  const clientId = process.env.GOOGLE_CLIENT_ID;
  if (!clientId) return null;
  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: "code",
    scope: "openid email profile",
    prompt: "select_account",
    state,
  });
  return `https://accounts.google.com/o/oauth2/v2/auth?${params}`;
}
