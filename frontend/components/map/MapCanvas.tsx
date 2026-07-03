"use client";

import dynamic from "next/dynamic";
import type { MapBounds, Place } from "@/types/place";
import { MapPlaceholder } from "./MapPlaceholder";
import type { KakaoMapLiveProps } from "./KakaoMapLive";

// 실지도는 SDK(window.kakao) 의존이라 SSR 비활성. 키가 없으면 아예 마운트하지 않고 placeholder.
const KakaoMapLive = dynamic(() => import("./KakaoMapLive"), {
  ssr: false,
  loading: () => <MapPlaceholder reason="loading" />,
});

const HAS_KEY = !!process.env.NEXT_PUBLIC_KAKAO_MAP_JS_KEY;

export function MapCanvas(props: KakaoMapLiveProps) {
  if (!HAS_KEY) return <MapPlaceholder reason="no-key" />;
  return <KakaoMapLive {...props} />;
}

export type { MapBounds, Place };
