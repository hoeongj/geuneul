"use client";

import { useEffect, useRef, useState } from "react";
import { CustomOverlayMap, Map, MapMarker, MarkerClusterer, useKakaoLoader } from "react-kakao-maps-sdk";
import { iconForCategory } from "@/lib/categories";
import { markerImage } from "@/lib/marker";
import { gradeOf } from "@/lib/survival";
import type { IconName } from "@/lib/icon-paths";
import type { MapBounds, Place, SurvivalGrade } from "@/types/place";
import { MapPlaceholder } from "./MapPlaceholder";

const markerImageCache = new globalThis.Map<string, ReturnType<typeof markerImage>>();

function cachedMarkerImage(icon: IconName, selected: boolean, grade: SurvivalGrade) {
  const key = `${icon}:${grade}:${selected ? "1" : "0"}`;
  const cached = markerImageCache.get(key);
  if (cached) return cached;
  const image = markerImage(icon, selected, grade);
  markerImageCache.set(key, image);
  return image;
}

export interface KakaoMapLiveProps {
  center: { lat: number; lng: number };
  current: { lat: number; lng: number; isFallback: boolean };
  places: Place[];
  selectedId: number | null;
  onSelect: (place: Place) => void;
  onBoundsChange: (bounds: MapBounds) => void;
  /** 값이 바뀌면 현재 위치로 팬(현재위치 FAB). */
  recenterKey: number;
  /** 지정 장소 검색(N5) 선택 결과 — 값이 바뀌면 그 좌표로 팬 + 핀 표시. */
  searchPin?: { lat: number; lng: number; name: string } | null;
}

export default function KakaoMapLive({
  center,
  current,
  places,
  selectedId,
  onSelect,
  onBoundsChange,
  recenterKey,
  searchPin,
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

  // 지정 장소 검색 선택(N5) → 그 좌표로 팬. onIdle→bounds 조회가 그 주변 우리 마커를 다시 채운다.
  // 파생 상태 저장이 아니라 "검색 선택"이라는 discrete 외부 이벤트를 제어형 map center에 동기화하는 것으로,
  // 위 recenter(현재위치) 이펙트와 동일한 성격이다. null 가드로 마운트 시 불필요한 팬을 막는다.
  const pinLat = searchPin?.lat;
  const pinLng = searchPin?.lng;
  useEffect(() => {
    if (pinLat == null || pinLng == null) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- 외부 선택 이벤트→제어형 center 동기화(파생상태 아님)
    setMapCenter({ lat: pinLat, lng: pinLng });
  }, [pinLat, pinLng]);

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
        {places.map((p) => {
          const icon = iconForCategory(p.category);
          const selected = p.id === selectedId;
          const grade = gradeOf(p);
          return (
            <MapMarker
              key={p.id}
              position={{ lat: p.lat, lng: p.lng }}
              image={cachedMarkerImage(icon, selected, grade)}
              onClick={() => onSelect(p)}
            />
          );
        })}
      </MarkerClusterer>

      {/* 지정 장소 검색 핀(N5) — 검색해서 이동한 목적지. 우리 마커(카테고리)와 구분되는 라벨 핀. */}
      {searchPin && (
        <CustomOverlayMap position={{ lat: searchPin.lat, lng: searchPin.lng }} yAnchor={1.25}>
          <div className="flex flex-col items-center">
            <span className="max-w-[160px] truncate rounded-full bg-forest px-2.5 py-1 text-[11px] font-bold text-cream shadow-sheet">
              {searchPin.name}
            </span>
            <span className="-mt-px h-2 w-2 rotate-45 bg-forest" />
          </div>
        </CustomOverlayMap>
      )}

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
