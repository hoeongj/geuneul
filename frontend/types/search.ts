// 지정 장소 검색 결과(N5) — 백엔드 /places/search 계약(PlaceSearchResult).
// 우리 places(DB)가 아니라 지도를 이동시킬 목적지 좌표(카카오 키워드 POI). 선택 시 지도 recenter.
export interface PlaceSearchResult {
  name: string;
  address: string | null;
  roadAddress: string | null;
  lat: number;
  lng: number;
  category: string | null;
}
