"use client";

import { createContext, useCallback, useContext, useState } from "react";
import type { Place } from "@/types/place";

interface SelectedState {
  id: number | null;
  /** 진입 시점의 목록 항목(있으면 distanceM 을 즉시 표기, 상세는 뒤이어 로드). */
  seed: Place | null;
  open: (place: Place | number) => void;
  close: () => void;
}

const SelectedCtx = createContext<SelectedState | null>(null);

// 장소 상세 = 라우트가 아니라 콘텐츠 위 오버레이(탭바 유지). 프로토타입 selectedId 와 동일.
export function SelectedPlaceProvider({ children }: { children: React.ReactNode }) {
  const [id, setId] = useState<number | null>(null);
  const [seed, setSeed] = useState<Place | null>(null);

  const open = useCallback((place: Place | number) => {
    if (typeof place === "number") {
      setId(place);
      setSeed(null);
    } else {
      setId(place.id);
      setSeed(place);
    }
  }, []);

  const close = useCallback(() => {
    setId(null);
    setSeed(null);
  }, []);

  return <SelectedCtx.Provider value={{ id, seed, open, close }}>{children}</SelectedCtx.Provider>;
}

export function useSelectedPlace(): SelectedState {
  const ctx = useContext(SelectedCtx);
  if (!ctx) throw new Error("useSelectedPlace must be used within SelectedPlaceProvider");
  return ctx;
}
