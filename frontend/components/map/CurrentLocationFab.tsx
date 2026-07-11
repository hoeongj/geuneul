"use client";

import { Icon } from "@/components/ui/Icon";

// 현재위치 FAB. 시트 바로 위 우측. 시트가 full 일 땐 숨김.
export function CurrentLocationFab({ hidden, onClick }: { hidden: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label="현재 위치로 이동"
      className={
        // 모바일: 반쯤 펼친 시트 바로 위 우측. 데스크톱(lg): 시트가 없으므로 지도 우하단 고정.
        "absolute right-3 bottom-[calc(46%+12px)] z-20 flex h-[46px] w-[46px] items-center justify-center rounded-[14px] bg-white text-forest shadow-fab transition-opacity lg:right-6 lg:bottom-6 " +
        (hidden ? "pointer-events-none opacity-0" : "opacity-100")
      }
    >
      <Icon name="locate" size={22} />
    </button>
  );
}
