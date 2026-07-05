"use client";

import { Map, MapMarker, useKakaoLoader } from "react-kakao-maps-sdk";
import type { IconName } from "@/lib/icon-paths";
import { markerImage } from "@/lib/marker";

// 상세 미니맵: 선택 장소 중심의 비대화형(드래그·줌 off) 실지도 + 마커.
export default function DetailMiniMapLive({ lat, lng, icon }: { lat: number; lng: number; icon: IconName }) {
  const [loading, error] = useKakaoLoader({
    appkey: process.env.NEXT_PUBLIC_KAKAO_MAP_JS_KEY ?? "",
    libraries: [],
  });

  if (loading || error) return <div className="h-[150px] w-full" style={{ background: "var(--color-map-base)" }} />;

  return (
    <Map center={{ lat, lng }} level={4} draggable={false} zoomable={false} className="h-[150px] w-full">
      <MapMarker position={{ lat, lng }} image={markerImage(icon, true)} />
    </Map>
  );
}
