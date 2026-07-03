"use client";

import { IconChip } from "@/components/ui/IconChip";
import { categoryLabel, iconForCategory } from "@/lib/categories";
import { formatDistance } from "@/lib/geo";
import type { Place } from "@/types/place";

// 바텀시트 리스트 로우: 좌 아이콘칩 · 이름+라벨·주소 · 우 거리 + "● 정보 부족".
// 마커/점수 색은 회색 고정(정보 부족) — survival_score 3색은 Reserved.
export function PlaceRow({ place, onClick }: { place: Place; onClick: () => void }) {
  const dist = formatDistance(place.distanceM);
  const label = categoryLabel(place.category, place.categoryLabel);
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-[44px] w-full items-center gap-3 border-b border-line-white-2 py-3 text-left last:border-b-0 active:bg-mint-3/40"
    >
      <IconChip icon={iconForCategory(place.category)} />
      <div className="min-w-0 flex-1">
        <div className="truncate text-[15px] font-bold text-ink">{place.name}</div>
        <div className="truncate text-[12px] text-ink-3">
          {label} · {place.address}
        </div>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1">
        {dist && <div className="text-[14px] font-extrabold text-teal">{dist}</div>}
        <div className="flex items-center gap-1 text-[10px] text-muted">
          <span className="h-1.5 w-1.5 rounded-full bg-status" />
          정보 부족
        </div>
      </div>
    </button>
  );
}
