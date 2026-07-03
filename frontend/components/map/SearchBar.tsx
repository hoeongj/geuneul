"use client";

import { Icon } from "@/components/ui/Icon";

// MVP 비동작 preview(반경검색은 지도 이동으로). 탭하면 토스트만.
export function SearchBar({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex h-11 w-full items-center gap-2.5 rounded-[14px] bg-white px-3.5 text-left shadow-search"
    >
      <span className="text-ink-3">
        <Icon name="search" size={18} />
      </span>
      <span className="flex-1 text-[14px] text-muted-2">지역·장소 검색</span>
      <span className="flex h-8 w-8 items-center justify-center rounded-[10px] bg-mint text-forest">
        <Icon name="filter" size={17} />
      </span>
    </button>
  );
}
