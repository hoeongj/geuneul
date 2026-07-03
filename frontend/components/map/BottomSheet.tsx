"use client";

import { Icon } from "@/components/ui/Icon";
import { PlaceRow } from "@/components/place/PlaceRow";
import type { Place } from "@/types/place";

interface BottomSheetProps {
  snap: "half" | "full";
  onToggleSnap: () => void;
  radius: number;
  places: Place[];
  loading: boolean;
  onSelectPlace: (place: Place) => void;
  onWiden: () => void;
}

function radiusLabel(m: number): string {
  return m >= 1000 ? `${(m / 1000).toFixed(m % 1000 === 0 ? 0 : 1)}km` : `${m}m`;
}

export function BottomSheet({ snap, onToggleSnap, radius, places, loading, onSelectPlace, onWiden }: BottomSheetProps) {
  const empty = !loading && places.length === 0;
  return (
    <section
      className="gn-sheet absolute inset-x-0 bottom-0 z-30 flex flex-col rounded-t-[22px] bg-white shadow-sheet"
      style={{ height: snap === "full" ? "82%" : "46%" }}
      aria-label="주변 장소 목록"
    >
      {/* 핸들 */}
      <button type="button" onClick={onToggleSnap} className="flex w-full justify-center pt-2.5 pb-1" aria-label={snap === "full" ? "접기" : "더보기"}>
        <span className="h-[5px] w-[38px] rounded-full" style={{ background: "#DBD8CC" }} />
      </button>

      {/* 헤더 */}
      <header className="flex items-center justify-between px-4 pb-2.5">
        <div className="min-w-0">
          <div className="text-[16px] font-extrabold text-ink">주변 {places.length}곳</div>
          <div className="text-[11px] text-muted">반경 {radiusLabel(radius)} · 지도를 움직여 탐색</div>
        </div>
        <button type="button" onClick={onToggleSnap} className="shrink-0 text-[13px] font-bold text-teal">
          {snap === "full" ? "접기" : "더보기"}
        </button>
      </header>

      {/* 리스트 / 상태 */}
      <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-4">
        {loading && places.length === 0 ? (
          <div className="flex h-full items-center justify-center py-10 text-[13px] text-muted">주변 장소를 불러오는 중…</div>
        ) : empty ? (
          <div className="flex flex-col items-center justify-center gap-2 py-10 text-center">
            <div className="mb-1 flex h-14 w-14 items-center justify-center rounded-full bg-mint text-status">
              <Icon name="mapicon" size={26} />
            </div>
            <div className="text-[15px] font-bold text-ink">이 근처엔 아직 정보가 없어요</div>
            <div className="text-[12.5px] text-ink-3">필터를 바꾸거나 반경을 넓혀보세요.</div>
            <button
              type="button"
              onClick={onWiden}
              className="mt-2 rounded-[12px] bg-forest px-4 py-2.5 text-[13px] font-bold text-cream"
            >
              반경 넓히기
            </button>
          </div>
        ) : (
          places.map((p) => <PlaceRow key={p.id} place={p} onClick={() => onSelectPlace(p)} />)
        )}
      </div>
    </section>
  );
}
