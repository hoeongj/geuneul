"use client";

import { Icon } from "@/components/ui/Icon";
import { PlaceRow } from "@/components/place/PlaceRow";
import type { Place } from "@/types/place";

// 주변 장소 리스트 본문(로딩·빈상태·행) — 모바일 바텀시트와 데스크톱 좌측 사이드바가 함께 쓴다.
// 바텀시트에서 분리해 두 레이아웃이 동일한 목록 UI를 공유(중복 제거).
export function PlaceListBody({
  places,
  loading,
  onSelectPlace,
  onWiden,
}: {
  places: Place[];
  loading: boolean;
  onSelectPlace: (place: Place) => void;
  onWiden: () => void;
}) {
  const empty = !loading && places.length === 0;
  return (
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
  );
}
