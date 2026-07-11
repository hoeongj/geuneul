"use client";

import { FilterChips } from "@/components/map/FilterChips";
import { PlaceListBody } from "@/components/map/PlaceListBody";
import { SearchBar } from "@/components/map/SearchBar";
import { radiusLabel } from "@/lib/geo";
import type { Category, Place } from "@/types/place";
import type { PlaceSearchResult } from "@/types/search";

// 데스크톱(≥lg) 전용 좌측 사이드바 — 검색·필터·주변 목록을 지도 왼쪽에 고정 패널로.
// 모바일에선 이 자리에 바텀시트가 뜬다(hidden lg:flex). 카카오/네이버맵 데스크톱과 동일한 지도+패널 구조.
export function MapSidebar({
  coords,
  radius,
  places,
  loading,
  cats,
  onToggleCat,
  onClearCats,
  onSearchSelect,
  onSelectPlace,
  onWiden,
}: {
  coords: { lat: number; lng: number } | null;
  radius: number;
  places: Place[];
  loading: boolean;
  cats: Category[];
  onToggleCat: (cat: Category) => void;
  onClearCats: () => void;
  onSearchSelect: (r: PlaceSearchResult) => void;
  onSelectPlace: (place: Place) => void;
  onWiden: () => void;
}) {
  return (
    <aside className="hidden lg:flex lg:h-full lg:w-[400px] lg:shrink-0 lg:flex-col lg:border-r lg:border-line-cream lg:bg-cream">
      <div className="space-y-2.5 border-b border-line-cream px-3.5 py-3.5">
        <SearchBar coords={coords} onSelect={onSearchSelect} />
        <FilterChips selected={cats} onToggle={onToggleCat} onClear={onClearCats} />
      </div>
      <header className="flex items-center justify-between px-4 pt-3.5 pb-2">
        <div className="min-w-0">
          <div className="text-[16px] font-extrabold text-ink">주변 {places.length}곳</div>
          <div className="text-[11px] text-muted">반경 {radiusLabel(radius)} · 지도를 움직여 탐색</div>
        </div>
      </header>
      <PlaceListBody places={places} loading={loading} onSelectPlace={onSelectPlace} onWiden={onWiden} />
    </aside>
  );
}
