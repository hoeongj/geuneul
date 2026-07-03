import type { Place } from "@/types/place";

// 카카오맵 딥링크(앱/웹 공통). 길찾기 = /link/to, 장소 보기 = /link/map.
export function kakaoDirectionsUrl(place: Pick<Place, "name" | "lat" | "lng">): string {
  return `https://map.kakao.com/link/to/${encodeURIComponent(place.name)},${place.lat},${place.lng}`;
}

export function kakaoMapUrl(place: Pick<Place, "name" | "lat" | "lng">): string {
  return `https://map.kakao.com/link/map/${encodeURIComponent(place.name)},${place.lat},${place.lng}`;
}
