"use client";

import { IconChip } from "@/components/ui/IconChip";
import { categoryLabel, iconForCategory } from "@/lib/categories";
import { formatDistance } from "@/lib/geo";
import { statusLabel } from "@/lib/survival";
import type { Place } from "@/types/place";

interface PlaceRowProps {
  place: Place;
  onClick: () => void;
  /** 작은 밀도(장소 선택 피커용). */
  compact?: boolean;
  /** survival_score 상태 배지 노출(홈 시트=true, 피커=false). */
  showStatus?: boolean;
}

// 장소 리스트 로우 — 좌 아이콘칩 · 이름+라벨·주소 · 우 거리 [+ survival_score 상태 배지].
// 상태 점 색 = survival_score 등급(초록 지금 좋음 / 노랑 보통 / 회색 정보 부족, ADR-0007).
export function PlaceRow({ place, onClick, compact = false, showStatus = true }: PlaceRowProps) {
  const dist = formatDistance(place.distanceM);
  const label = categoryLabel(place.category, place.categoryLabel);
  const status = statusLabel(place.survival);
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        "flex min-h-[44px] w-full items-center gap-3 border-b border-line-white-2 text-left transition-colors last:border-b-0 active:bg-mint-3/40 lg:hover:bg-mint-3/30 " +
        (compact ? "py-2.5" : "py-3")
      }
    >
      <IconChip icon={iconForCategory(place.category)} size={compact ? 36 : 42} iconSize={compact ? 17 : 20} />
      <div className="min-w-0 flex-1">
        <div className={"truncate font-bold text-ink " + (compact ? "text-[14px]" : "text-[15px]")}>{place.name}</div>
        <div className={"truncate text-ink-3 " + (compact ? "text-[11.5px]" : "text-[12px]")}>
          {label} · {place.address}
        </div>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1">
        {dist && <div className={"font-extrabold text-teal " + (compact ? "text-[12.5px]" : "text-[14px]")}>{dist}</div>}
        {showStatus && (
          <div className="flex items-center gap-1 text-[10px] text-muted">
            <span className="h-1.5 w-1.5 rounded-full" style={{ background: status.color }} />
            {status.label}
          </div>
        )}
      </div>
    </button>
  );
}
