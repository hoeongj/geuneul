"use client";

import { useRef, useState } from "react";
import { Icon } from "@/components/ui/Icon";
import { PlaceRow } from "@/components/place/PlaceRow";
import type { Place } from "@/types/place";

// 하단 시트 3단 스냅(N4): peek(핸들만 보여 지도 크게)·half(기본)·full. 드래그로 가까운 단으로,
// 탭으로 펼침/접힘. peek는 헤더 두 줄이 안 잘리게 px로 고정하고 half/full은 컨테이너 대비 %.
export type SheetSnap = "peek" | "half" | "full";

const PEEK_PX = 46; // 핸들 + 한 줄 라벨만 — peek에선 목록을 숨겨 지도를 거의 꽉 채운다
const HALF_FRACTION = 0.46;
const FULL_FRACTION = 0.82;
const TAP_THRESHOLD_PX = 6; // 이 이하로 움직이면 드래그가 아니라 탭으로 본다

interface BottomSheetProps {
  snap: SheetSnap;
  onSnapChange: (next: SheetSnap) => void;
  radius: number;
  places: Place[];
  loading: boolean;
  onSelectPlace: (place: Place) => void;
  onWiden: () => void;
}

function radiusLabel(m: number): string {
  return m >= 1000 ? `${(m / 1000).toFixed(m % 1000 === 0 ? 0 : 1)}km` : `${m}m`;
}

function snapHeightPx(snap: SheetSnap, containerH: number): number {
  if (snap === "peek") return PEEK_PX;
  return (snap === "full" ? FULL_FRACTION : HALF_FRACTION) * containerH;
}

function snapHeightCss(snap: SheetSnap): string {
  if (snap === "peek") return `${PEEK_PX}px`;
  return snap === "full" ? "82%" : "46%";
}

export function BottomSheet({ snap, onSnapChange, radius, places, loading, onSelectPlace, onWiden }: BottomSheetProps) {
  const empty = !loading && places.length === 0;
  const sheetRef = useRef<HTMLElement>(null);
  // 드래그 세션 상태(리렌더 유발 안 하는 값은 ref로). currentH는 pointerup 시 최종 높이 판정에 쓴다.
  const dragRef = useRef<{ startY: number; startH: number; containerH: number; currentH: number; dragged: boolean } | null>(null);
  const [dragH, setDragH] = useState<number | null>(null); // 드래그 중 라이브 px 높이, 아니면 null

  const onPointerDown = (e: React.PointerEvent) => {
    const containerH = sheetRef.current?.parentElement?.clientHeight
      ?? (typeof window !== "undefined" ? window.innerHeight : 800);
    const startH = snapHeightPx(snap, containerH);
    dragRef.current = { startY: e.clientY, startH, containerH, currentH: startH, dragged: false };
    (e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId);
    setDragH(startH);
  };

  const onPointerMove = (e: React.PointerEvent) => {
    const d = dragRef.current;
    if (!d) return;
    const dy = d.startY - e.clientY; // 위로 끌면 양수 → 높이 증가
    if (Math.abs(dy) > TAP_THRESHOLD_PX) d.dragged = true;
    const minH = snapHeightPx("peek", d.containerH);
    const maxH = snapHeightPx("full", d.containerH);
    const next = Math.min(maxH, Math.max(minH, d.startH + dy));
    d.currentH = next;
    setDragH(next);
  };

  const endDrag = (e: React.PointerEvent) => {
    const d = dragRef.current;
    dragRef.current = null;
    setDragH(null);
    if (!d) return;
    (e.currentTarget as HTMLElement).releasePointerCapture?.(e.pointerId);
    if (!d.dragged) return; // 탭이면 native click(onToggle)에 맡긴다
    // 가장 가까운 스냅으로
    const order: SheetSnap[] = ["peek", "half", "full"];
    const nearest = order.reduce((best, s) =>
      Math.abs(snapHeightPx(s, d.containerH) - d.currentH) < Math.abs(snapHeightPx(best, d.containerH) - d.currentH)
        ? s : best, order[0]);
    onSnapChange(nearest);
  };

  // 탭(드래그 아님) → 단계 전환. peek→half(목록 열기)·half→full·full→half. peek로 완전히 접기는 아래로 드래그.
  const onToggle = () => {
    if (dragRef.current?.dragged) return;
    onSnapChange(snap === "peek" ? "half" : snap === "full" ? "half" : "full");
  };

  const heightStyle = dragH != null ? `${dragH}px` : snapHeightCss(snap);

  return (
    <section
      ref={sheetRef}
      className="gn-sheet absolute inset-x-0 bottom-0 z-30 flex flex-col rounded-t-[22px] bg-white shadow-sheet"
      style={{ height: heightStyle, transition: dragH != null ? "none" : "height 0.25s ease" }}
      aria-label="주변 장소 목록"
    >
      {/* 핸들 — 드래그(3단 스냅)·탭(펼침/접힘) 겸용 */}
      <button
        type="button"
        onClick={onToggle}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        className="flex w-full touch-none justify-center pt-2.5 pb-1"
        aria-label={snap === "full" ? "시트 접기" : "시트 펼치기 · 드래그로 크기 조절"}
      >
        <span className="h-[5px] w-[38px] rounded-full" style={{ background: "var(--color-sheet-handle)" }} />
      </button>

      {snap === "peek" ? (
        // peek — 목록·헤더를 숨긴 얇은 재열기 바(지도 최대). 탭/위로 드래그하면 목록이 열린다.
        <button
          type="button"
          onClick={onToggle}
          className="-mt-1 flex w-full items-center justify-center gap-1.5 pb-1 text-[13px] font-bold text-ink-2"
          aria-label={`주변 ${places.length}곳 · 목록 열기`}
        >
          주변 {places.length}곳
          <Icon name="chev" size={13} className="-rotate-90 text-muted-3" />
        </button>
      ) : (
        <>
          {/* 헤더 */}
          <header className="flex items-center justify-between px-4 pb-2.5">
            <div className="min-w-0">
              <div className="text-[16px] font-extrabold text-ink">주변 {places.length}곳</div>
              <div className="text-[11px] text-muted">반경 {radiusLabel(radius)} · 지도를 움직여 탐색</div>
            </div>
            <button type="button" onClick={onToggle} className="shrink-0 text-[13px] font-bold text-teal">
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
        </>
      )}
    </section>
  );
}
