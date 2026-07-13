"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { FALLBACK_CENTER } from "@/lib/geo";

type GeoStatus = "idle" | "locating" | "granted" | "cached" | "fallback";

const LAST_LOCATION_KEY = "geuneul:last-location:v1";
const LAST_LOCATION_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;

type SavedLocation = { lat: number; lng: number; accuracy: number | null; savedAt: number };

function readSavedLocation(): SavedLocation | null {
  try {
    const raw = window.localStorage.getItem(LAST_LOCATION_KEY);
    if (!raw) return null;
    const saved = JSON.parse(raw) as SavedLocation;
    if (
      !Number.isFinite(saved.lat) ||
      !Number.isFinite(saved.lng) ||
      !Number.isFinite(saved.savedAt) ||
      Date.now() - saved.savedAt > LAST_LOCATION_MAX_AGE_MS
    ) {
      window.localStorage.removeItem(LAST_LOCATION_KEY);
      return null;
    }
    return saved;
  } catch {
    return null;
  }
}

function saveLocation(location: SavedLocation) {
  try {
    window.localStorage.setItem(LAST_LOCATION_KEY, JSON.stringify(location));
  } catch {
    // 비공개 모드 등 저장소를 쓸 수 없는 경우에도 현재 세션 위치 기능은 정상 동작한다.
  }
}

interface GeoState {
  lat: number;
  lng: number;
  accuracy: number | null;
  isFallback: boolean;
  status: GeoStatus;
  /** 다시 현재 위치로(FAB). 위치를 받았는지 반환한다. */
  locate: () => Promise<boolean>;
}

const GeoCtx = createContext<GeoState | null>(null);

// 지도/급해요가 같은 위치 상태를 공유한다. 자동 요청은 이미 허용된 권한에만 하고,
// 그 외에는 최근 위치를 복원해 중복 프롬프트와 첫 화면 공백을 줄인다.
export function GeoProvider({ children }: { children: React.ReactNode }) {
  // 저장된 최근 위치는 첫 클라이언트 렌더에서만 읽어, 진입 직후 폴백으로 한 번 그렸다가 바꾸는 깜빡임을 없앤다.
  const [initialSaved] = useState<SavedLocation | null>(() => (typeof window === "undefined" ? null : readSavedLocation()));
  const [coords, setCoords] = useState<{ lat: number; lng: number }>(() =>
    initialSaved ? { lat: initialSaved.lat, lng: initialSaved.lng } : { lat: FALLBACK_CENTER.lat, lng: FALLBACK_CENTER.lng },
  );
  const [accuracy, setAccuracy] = useState<number | null>(() => initialSaved?.accuracy ?? null);
  const [status, setStatus] = useState<GeoStatus>(() => (initialSaved ? "cached" : "idle"));
  const requested = useRef(false);

  const locate = useCallback((): Promise<boolean> => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      setStatus("fallback");
      return Promise.resolve(false);
    }
    setStatus("locating");
    return new Promise((resolve) => {
      const succeed = (pos: GeolocationPosition) => {
        setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        setAccuracy(pos.coords.accuracy);
        setStatus("granted");
        saveLocation({ lat: pos.coords.latitude, lng: pos.coords.longitude, accuracy: pos.coords.accuracy, savedAt: Date.now() });
        resolve(true);
      };
      const fail = (error: GeolocationPositionError) => {
        // 고정밀 GPS가 실내·절전 모드에서 늦거나 실패하는 경우, 이미 허용된 권한으로 네트워크 위치를 한 번 더 시도한다.
        // 권한 거부는 재시도해도 프롬프트만 반복될 수 있어 즉시 끝낸다.
        if (error.code === error.PERMISSION_DENIED) {
          setStatus(readSavedLocation() ? "cached" : "fallback");
          resolve(false);
          return;
        }
        navigator.geolocation.getCurrentPosition(
          succeed,
          () => {
            setStatus(readSavedLocation() ? "cached" : "fallback");
            resolve(false);
          },
          { enableHighAccuracy: false, timeout: 5_000, maximumAge: 5 * 60_000 },
        );
      };
      navigator.geolocation.getCurrentPosition(succeed, fail, {
        enableHighAccuracy: true,
        timeout: 8_000,
        maximumAge: 30_000,
      });
    });
  }, []);

  useEffect(() => {
    if (requested.current) return;
    requested.current = true;
    if (typeof navigator === "undefined") return;

    // 브라우저가 이미 허용한 경우에만 자동 갱신한다. 아직 선택하지 않은 사용자에게는
    // 진입 직후 권한창을 띄우지 않고, 현재 위치 버튼을 눌렀을 때 한 번만 묻는다.
    if (!navigator.permissions?.query) return;
    let permission: PermissionStatus | null = null;
    navigator.permissions
      .query({ name: "geolocation" })
      .then((result) => {
        permission = result;
        if (result.state === "granted") void locate();
        result.onchange = () => {
          if (result.state === "granted") void locate();
          else if (result.state === "denied") setStatus(readSavedLocation() ? "cached" : "fallback");
        };
      })
      .catch(() => {
        // Permissions API가 없는 브라우저는 버튼을 눌렀을 때만 Geolocation API를 호출한다.
      });
    return () => {
      if (permission) permission.onchange = null;
    };
  }, [locate]);

  return (
    <GeoCtx.Provider
      value={{
        lat: coords.lat,
        lng: coords.lng,
        accuracy,
        isFallback: status !== "granted" && status !== "cached",
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
