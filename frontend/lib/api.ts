// 클라이언트 fetch 계층. 브라우저는 항상 동일 오리진 /api/* 프록시만 호출한다(ALB 직접 호출 금지).
import type { MapBounds, Place, Report, ReportCreatePayload, Scenario } from "@/types/place";
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

async function getJson<T>(url: string): Promise<T> {
  const res = await fetch(url);
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

// 휘발성 제보 생성(익명 허용). 429 = 레이트리밋 — 메시지로 안내.
export async function createReport(payload: ReportCreatePayload): Promise<Report> {
  const res = await fetch(`/api/reports`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw await toApiError(res);
  return res.json() as Promise<Report>;
}
