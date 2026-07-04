"use client";

import { useEffect, useRef, useState } from "react";
import { CustomOverlayMap, Map, MapMarker, MarkerClusterer, useKakaoLoader } from "react-kakao-maps-sdk";
import { iconForCategory } from "@/lib/categories";
import { markerImage } from "@/lib/marker";
import { gradeOf } from "@/lib/survival";
import type { MapBounds, Place } from "@/types/place";
import { MapPlaceholder } from "./MapPlaceholder";

export interface KakaoMapLiveProps {
  center: { lat: number; lng: number };
  current: { lat: number; lng: number; isFallback: boolean };
  places: Place[];
  selectedId: number | null;
  onSelect: (place: Place) => void;
  onBoundsChange: (bounds: MapBounds) => void;
  /** 값이 바뀌면 현재 위치로 팬(현재위치 FAB). */
  recenterKey: number;
}

export default function KakaoMapLive({
  center,
  current,
  places,
  selectedId,
  onSelect,
  onBoundsChange,
  recenterKey,
}: KakaoMapLiveProps) {
  const [loading, error] = useKakaoLoader({
    appkey: process.env.NEXT_PUBLIC_KAKAO_MAP_JS_KEY ?? "",
    libraries: ["clusterer"],
  });

  // 제어 center: 최초 + FAB 시에만 갱신(사용자 팬과 싸우지 않도록).
  const [mapCenter, setMapCenter] = useState(center);
  const firstRecenter = useRef(true);
  useEffect(() => {
    if (firstRecenter.current) {
      firstRecenter.current = false;
      return;
    }
    setMapCenter({ lat: current.lat, lng: current.lng });
  }, [recenterKey, current.lat, current.lng]);

  if (error) return <MapPlaceholder reason="error" />;
  if (loading) return <MapPlaceholder reason="loading" />;

  return (
    <Map
      center={mapCenter}
      isPanto
      level={5}
      className="h-full w-full"
      onIdle={(map) => {
        const b = map.getBounds();
        const sw = b.getSouthWest();
        const ne = b.getNorthEast();
        onBoundsChange({ west: sw.getLng(), south: sw.getLat(), east: ne.getLng(), north: ne.getLat() });
      }}
    >
      <MarkerClusterer averageCenter minLevel={7} disableClickZoom={false}>
        {places.map((p) => (
          <MapMarker
            key={p.id}
            position={{ lat: p.lat, lng: p.lng }}
            image={markerImage(iconForCategory(p.category), p.id === selectedId, gradeOf(p))}
            onClick={() => onSelect(p)}
          />
        ))}
      </MarkerClusterer>

      {/* 현재 위치: 파란 점 + 정확도 펄스링. 폴백(권한 거부)일 땐 살짝 흐리게. */}
      <CustomOverlayMap position={{ lat: current.lat, lng: current.lng }} xAnchor={0.5} yAnchor={0.5}>
        <div className="relative flex items-center justify-center" style={{ opacity: current.isFallback ? 0.65 : 1 }}>
          <span
            className="gn-pulse-ring absolute h-4 w-4 rounded-full"
            style={{ background: "rgba(43,111,246,.35)" }}
          />
          <span
            className="h-3.5 w-3.5 rounded-full border-2 border-white"
            style={{ background: "var(--color-locate)", boxShadow: "0 1px 4px rgba(0,0,0,.3)" }}
          />
        </div>
      </CustomOverlayMap>
    </Map>
  );
}
