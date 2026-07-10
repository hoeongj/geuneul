// 클라이언트 fetch 계층. 브라우저는 항상 동일 오리진 /api/* 프록시만 호출한다(ALB 직접 호출 금지).
import type { SurgeInfo } from "@/types/alert";
import type { ReactionState, ReactionTarget, ReactionType, ReviewComment } from "@/types/community";
import type { MapBounds, Place, Report, ReportCreatePayload, Scenario } from "@/types/place";
import type { PopularTimesSlot } from "@/types/popular";
import type { PhotoPresignResult, PhotoPurpose } from "@/types/photo";
import type { Review, ReviewCreatePayload, ReviewListResponse } from "@/types/review";
import type { User } from "@/types/user";
import { boundsParam } from "./geo";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

// 응답 에러 → ApiError. 백엔드는 Spring 기본 JSON({timestamp,status,error,path,message}) 또는
// 프록시 에러({error,message}) — message를 우선 살리고 없으면 error, 그래도 없으면 statusText.
async function toApiError(res: Response): Promise<ApiError> {
  let msg = res.statusText;
  try {
    const body = await res.json();
    msg = body?.message ?? body?.error ?? msg;
  } catch {
    /* 바디가 JSON이 아님 */
  }
  return new ApiError(res.status, msg);
}

// 동일 오리진 프록시가 10s 상한을 갖지만, 프록시 라우트 자체가 멈추는 경우를 대비해 클라이언트도 상한을 둔다.
const CLIENT_TIMEOUT_MS = 12_000;

async function getJson<T>(url: string): Promise<T> {
  const res = await fetch(url, { signal: AbortSignal.timeout(CLIENT_TIMEOUT_MS) });
  if (!res.ok) throw await toApiError(res);
  return res.json() as Promise<T>;
}

// 홈 지도 마커 = 뷰포트 bounds 조회(전 카테고리, 클라에서 칩 필터).
export function fetchByBounds(bounds: MapBounds): Promise<Place[]> {
  const qs = new URLSearchParams({ bounds: boundsParam(bounds), limit: "100" });
  return getJson<Place[]>(`/api/places?${qs}`);
}

// 시트 리스트 = 현재 위치 반경 조회(distanceM 포함).
export function fetchByRadius(params: { lat: number; lng: number; radius: number }): Promise<Place[]> {
  const qs = new URLSearchParams({
    lat: String(params.lat),
    lng: String(params.lng),
    radius: String(params.radius),
    limit: "100",
  });
  return getJson<Place[]>(`/api/places?${qs}`);
}

// 단건 상세.
export function fetchPlace(id: number): Promise<Place> {
  return getJson<Place>(`/api/places/${id}`);
}

// 급해요 = 시나리오별 nearest 팬아웃(서버 프록시가 병합·정렬).
export function fetchUrgent(params: { scenario: Scenario; lat: number; lng: number }): Promise<Place[]> {
  const qs = new URLSearchParams({
    scenario: params.scenario,
    lat: String(params.lat),
    lng: String(params.lng),
  });
  return getJson<Place[]>(`/api/urgent?${qs}`);
}

// 제보 기본 장소 = 현재 위치 최근접(카테고리 무관).
export function fetchNearestAny(params: { lat: number; lng: number; limit?: number }): Promise<Place[]> {
  const qs = new URLSearchParams({
    lat: String(params.lat),
    lng: String(params.lng),
    limit: String(params.limit ?? 1),
  });
  return getJson<Place[]>(`/api/places/nearest?${qs}`);
}

// 장소의 최근 유효 제보(미만료, 최신순 top20).
export function fetchPlaceReports(placeId: number): Promise<Report[]> {
  return getJson<Report[]>(`/api/places/${placeId}/reports`);
}

// 장소의 시간대별 혼잡 파생(요일×시간, 만료 포함 이력 채굴). 백엔드 Redis 1h 캐시.
export function fetchPopularTimes(placeId: number): Promise<PopularTimesSlot[]> {
  return getJson<PopularTimesSlot[]>(`/api/places/${placeId}/popular-times`);
}

// 뷰포트 내 제보 급증 스냅샷(폴백/초기 상태). SSE(/api/alerts/stream)가 공백을 실시간으로 메운다.
export function fetchSurge(bounds: MapBounds): Promise<SurgeInfo[]> {
  const qs = new URLSearchParams({ bounds: boundsParam(bounds), limit: "50" });
  return getJson<SurgeInfo[]>(`/api/alerts/surge?${qs}`);
}

// 내 프로필 — 미로그인(401)이면 null. 그 외 오류는 throw(호출부에서 구분).
export async function fetchMe(): Promise<User | null> {
  const res = await fetch(`/api/me`, { signal: AbortSignal.timeout(CLIENT_TIMEOUT_MS) });
  if (res.status === 401) return null;
  if (!res.ok) throw await toApiError(res);
  return res.json() as Promise<User>;
}

// 로그아웃 — 세션 쿠키 삭제(서버 무상태).
export async function logout(): Promise<void> {
  await fetch(`/api/auth/logout`, { method: "POST", signal: AbortSignal.timeout(CLIENT_TIMEOUT_MS) });
}

// 휘발성 제보 생성(익명 허용). 429 = 레이트리밋 — 메시지로 안내.
export async function createReport(payload: ReportCreatePayload): Promise<Report> {
  const res = await fetch(`/api/reports`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(CLIENT_TIMEOUT_MS),
  });
  if (!res.ok) throw await toApiError(res);
  return res.json() as Promise<Report>;
}

// 장소의 영구 후기 목록(공개, 최신순 페이지네이션). survival_score(휘발성 제보)와 분리된 평판.
export function fetchPlaceReviews(placeId: number, page = 0): Promise<ReviewListResponse> {
  const qs = new URLSearchParams({ page: String(page), size: "20" });
  return getJson<ReviewListResponse>(`/api/places/${placeId}/reviews?${qs}`);
}

// 후기 작성/수정(로그인 필요, 장소당 1건 upsert). 401 = 미로그인 — 호출부가 안내 메시지로 처리.
export async function createReview(payload: ReviewCreatePayload): Promise<Review> {
  const res = await fetch(`/api/reviews`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(CLIENT_TIMEOUT_MS),
  });
  if (!res.ok) throw await toApiError(res);
  return res.json() as Promise<Review>;
}

// 후기 댓글 목록(공개, 오래된 순).
export function fetchReviewComments(reviewId: number): Promise<ReviewComment[]> {
  return getJson<ReviewComment[]>(`/api/reviews/${reviewId}/comments`);
}

// 후기 댓글 작성(로그인 필요). 401 = 미로그인 — 호출부가 안내.
export async function createReviewComment(reviewId: number, comment: string): Promise<ReviewComment> {
  const res = await fetch(`/api/reviews/${reviewId}/comments`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ comment }),
    signal: AbortSignal.timeout(CLIENT_TIMEOUT_MS),
  });
  if (!res.ok) throw await toApiError(res);
  return res.json() as Promise<ReviewComment>;
}

// 리액션 토글(유용해요). add=true면 POST(추가·멱등), false면 DELETE(취소). 응답은 {reacted,count}.
export async function toggleReaction(params: {
  targetType: ReactionTarget;
  targetId: number;
  type?: ReactionType;
  add: boolean;
}): Promise<ReactionState> {
  const res = await fetch(`/api/reactions`, {
    method: params.add ? "POST" : "DELETE",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ targetType: params.targetType, targetId: params.targetId, type: params.type ?? "HELPFUL" }),
    signal: AbortSignal.timeout(CLIENT_TIMEOUT_MS),
  });
  if (!res.ok) throw await toApiError(res);
  return res.json() as Promise<ReactionState>;
}

// 사진 업로드 presign 발급 — S3로 직접 PUT할 URL을 받는다. purpose=review는 로그인 필요(401은 호출부가 처리).
export async function presignPhoto(params: {
  contentType: string;
  contentLength: number;
  purpose: PhotoPurpose;
}): Promise<PhotoPresignResult> {
  const res = await fetch(`/api/photos/presign`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(params),
    signal: AbortSignal.timeout(CLIENT_TIMEOUT_MS),
  });
  if (!res.ok) throw await toApiError(res);
  return res.json() as Promise<PhotoPresignResult>;
}

// presign으로 받은 URL에 파일을 직접 PUT — 서명 시 실은 Content-Type/Content-Length와 정확히 일치해야
// S3가 받아준다(PhotoService 주석 참고). 이 요청은 동일 오리진 프록시를 거치지 않고 S3로 바로 나간다
// (presigned URL 자체가 인가 수단이라 BFF를 거칠 이유가 없다 — 오히려 대용량 바이너리가 Vercel 함수를 왕복하지 않아 유리).
export async function uploadPhotoToS3(presigned: PhotoPresignResult, file: File): Promise<void> {
  const res = await fetch(presigned.uploadUrl, {
    method: "PUT",
    headers: { "content-type": file.type },
    body: file,
  });
  if (!res.ok) throw new Error(`사진 업로드에 실패했어요 (${res.status})`);
}
