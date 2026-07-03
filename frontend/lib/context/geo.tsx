"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { FALLBACK_CENTER } from "@/lib/geo";

type GeoStatus = "idle" | "locating" | "granted" | "fallback";

interface GeoState {
  lat: number;
  lng: number;
  accuracy: number | null;
  isFallback: boolean;
  status: GeoStatus;
  /** 다시 현재 위치로(FAB). */
  locate: () => void;
}

const GeoCtx = createContext<GeoState | null>(null);

// 위치 권한을 한 번 요청해 지도/급해요가 공유(중복 프롬프트 방지).
// 거부/실패 시 폴백 센터(서울 동작구)로 초기화하고 isFallback=true.
export function GeoProvider({ children }: { children: React.ReactNode }) {
  const [coords, setCoords] = useState<{ lat: number; lng: number }>({
    lat: FALLBACK_CENTER.lat,
    lng: FALLBACK_CENTER.lng,
  });
  const [accuracy, setAccuracy] = useState<number | null>(null);
  const [status, setStatus] = useState<GeoStatus>("idle");
  const requested = useRef(false);

  const locate = useCallback(() => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setStatus("fallback");
      return;
    }
    setStatus("locating");
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        setAccuracy(pos.coords.accuracy);
        setStatus("granted");
      },
      () => {
        // 거부/타임아웃 → 폴백 유지(공포·차단 톤 없이 조용히).
        setStatus("fallback");
      },
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 30_000 },
    );
  }, []);

  useEffect(() => {
    if (requested.current) return;
    requested.current = true;
    locate();
  }, [locate]);

  return (
    <GeoCtx.Provider
      value={{
        lat: coords.lat,
        lng: coords.lng,
        accuracy,
        isFallback: status !== "granted",
        status,
        locate,
      }}
    >
      {children}
    </GeoCtx.Provider>
  );
}

export function useGeo(): GeoState {
  const ctx = useContext(GeoCtx);
  if (!ctx) throw new Error("useGeo must be used within GeoProvider");
  return ctx;
}
