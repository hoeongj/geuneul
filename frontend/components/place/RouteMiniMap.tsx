"use client";

import dynamic from "next/dynamic";
import type { RouteResult } from "@/types/route";
import type { Category } from "@/types/place";

const HAS_KEY = !!process.env.NEXT_PUBLIC_KAKAO_MAP_JS_KEY;

// 실지도는 SDK(window.kakao) 의존이라 SSR 비활성(DetailMiniMap과 동일 패턴).
const Live = dynamic(() => import("./RouteMiniMapLive"), {
  ssr: false,
  loading: () => <div className="h-[170px] w-full" style={{ background: "var(--color-map-base)" }} />,
});

// 키 없으면 경로 요약 텍스트만(폴리라인 지도는 키 필요). 경유지 정보는 상세 배너가 이미 보여준다.
export function RouteMiniMap({ route, destCategory }: { route: RouteResult; destCategory: Category }) {
  if (!HAS_KEY) return null;
  return <Live route={route} destCategory={destCategory} />;
}
