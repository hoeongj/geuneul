// 클라이언트 fetch 계층. 브라우저는 항상 동일 오리진 /api/* 프록시만 호출한다(ALB 직접 호출 금지).
import type { MapBounds, Place, Scenario } from "@/types/place";
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

async function getJson<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) {
    // 백엔드 에러는 Spring 기본 JSON {timestamp,status,error,path}. 바디의 error/message 를 최대한 살린다.
    let msg = res.statusText;
    try {
      const body = await res.json();
      msg = body?.error ?? body?.message ?? msg;
    } catch {
      /* noop */
    }
    throw new ApiError(res.status, msg);
  }
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
