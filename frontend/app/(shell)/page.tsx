"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { BottomSheet } from "@/components/map/BottomSheet";
import { CurrentLocationFab } from "@/components/map/CurrentLocationFab";
import { FilterChips } from "@/components/map/FilterChips";
import { MapCanvas } from "@/components/map/MapCanvas";
import { SearchBar } from "@/components/map/SearchBar";
import { SurgeBanner } from "@/components/map/SurgeBanner";
import { useGeo } from "@/lib/context/geo";
import { useSelectedPlace } from "@/lib/context/selected";
import { useToast } from "@/lib/context/toast";
import { DEFAULT_RADIUS, WIDENED_RADIUS } from "@/lib/geo";
import { useDebouncedCallback } from "@/lib/hooks";
import { useBoundsPlaces, useRadiusPlaces } from "@/lib/queries";
import { useSurgeAlerts } from "@/lib/surge";
import type { SurgeInfo } from "@/types/alert";
import type { Category, MapBounds, Place } from "@/types/place";

function byCategories(places: Place[], cats: Category[]): Place[] {
  if (cats.length === 0) return places;
  return places.filter((p) => cats.includes(p.category));
}

function byDistance(places: Place[]): Place[] {
  return [...places].sort((a, b) => (a.distanceM ?? Infinity) - (b.distanceM ?? Infinity));
}

export default function MapPage() {
  const geo = useGeo();
  const selected = useSelectedPlace();
  const { show } = useToast();

  const [bounds, setBounds] = useState<MapBounds | null>(null);
  const [cats, setCats] = useState<Category[]>([]);
  const [radius, setRadius] = useState(DEFAULT_RADIUS);
  const [snap, setSnap] = useState<"half" | "full">("half");
  const [recenterKey, setRecenterKey] = useState(0);

  const coords = { lat: geo.lat, lng: geo.lng };

  const boundsQuery = useBoundsPlaces(bounds);
  const radiusQuery = useRadiusPlaces(coords, radius);

  // 위치 권한이 폴백 → 실제로 잡히면 한 번 지도 재중심.
  const grantedRef = useRef(false);
  useEffect(() => {
    if (geo.status === "granted" && !grantedRef.current) {
      grantedRef.current = true;
      setRecenterKey((k) => k + 1);
    }
  }, [geo.status]);

  const markers = useMemo(() => byCategories(boundsQuery.data ?? [], cats), [boundsQuery.data, cats]);
  const listPlaces = useMemo(() => byDistance(byCategories(radiusQuery.data ?? [], cats)), [radiusQuery.data, cats]);

  // 실시간 제보 급증(A4) — 뷰포트 스냅샷 + SSE. 뷰포트 안의 새 급증이 오면 중립 토스트(§6).
  const onSurge = (s: SurgeInfo) => show(s.message);
  const { surges } = useSurgeAlerts(bounds, onSurge);

  const onBoundsChange = useDebouncedCallback((b: MapBounds) => setBounds(b), 250);

  const toggleCat = (cat: Category) =>
    setCats((prev) => (prev.includes(cat) ? prev.filter((c) => c !== cat) : [...prev, cat]));

  const widen = () => {
    setCats([]);
    setRadius(WIDENED_RADIUS);
    show(`반경 ${(WIDENED_RADIUS / 1000).toFixed(1)}km로 넓혔어요`);
  };

  const recenter = () => {
    setRecenterKey((k) => k + 1);
    geo.locate();
    show(geo.isFallback ? "현재 위치 권한을 확인해 주세요" : "현재 위치로 이동했어요");
  };

  return (
    <div className="relative h-full w-full">
      <MapCanvas
        center={coords}
        current={{ lat: geo.lat, lng: geo.lng, isFallback: geo.isFallback }}
        places={markers}
        selectedId={selected.id}
        onSelect={selected.open}
        onBoundsChange={onBoundsChange}
        recenterKey={recenterKey}
      />

      {/* 상단 검색 + 필터 + 급증 배너(A4) */}
      <div className="pointer-events-none absolute inset-x-0 top-3 z-20 space-y-2.5 px-3">
        <div className="pointer-events-auto">
          <SearchBar onClick={() => show("검색은 준비 중이에요 · 지도를 움직여 탐색하세요")} />
        </div>
        <div className="pointer-events-auto">
          <FilterChips selected={cats} onToggle={toggleCat} onClear={() => setCats([])} />
        </div>
        <SurgeBanner surges={surges} onSelect={selected.open} />
      </div>

      <CurrentLocationFab hidden={snap === "full"} onClick={recenter} />

      <BottomSheet
        snap={snap}
        onToggleSnap={() => setSnap((s) => (s === "full" ? "half" : "full"))}
        radius={radius}
        places={listPlaces}
        loading={radiusQuery.isLoading}
        onSelectPlace={selected.open}
        onWiden={widen}
      />
    </div>
  );
}
