"use client";

import dynamic from "next/dynamic";
import { Icon } from "@/components/ui/Icon";
import { iconForCategory } from "@/lib/categories";
import type { RouteResult } from "@/types/route";
import type { Category } from "@/types/place";

const HAS_KEY = !!process.env.NEXT_PUBLIC_KAKAO_MAP_JS_KEY;

// 실지도는 SDK(window.kakao) 의존이라 SSR 비활성(DetailMiniMap과 동일 패턴).
const Live = dynamic(() => import("./RouteMiniMapLive"), {
  ssr: false,
  loading: () => <div className="h-[170px] w-full" style={{ background: "var(--color-map-base)" }} />,
});

function Placeholder({ destCategory }: { destCategory: Category }) {
  return (
    <div className="relative h-[170px] w-full overflow-hidden" style={{ background: "var(--color-map-base)" }}>
      <div className="absolute left-[9%] top-[18%] h-16 w-24 rounded-2xl" style={{ background: "var(--color-map-park)" }} />
      <div className="absolute right-[11%] bottom-[15%] h-14 w-20 rounded-2xl" style={{ background: "var(--color-map-block)" }} />
      <div className="absolute inset-x-[16%] top-1/2 h-2 -translate-y-1/2 rounded-full" style={{ background: "var(--color-map-road)" }} />
      <div className="absolute right-[24%] top-1/2 -translate-y-full text-forest">
        <div className="flex h-8 w-8 items-center justify-center rounded-full border-[2.5px] border-forest bg-white shadow">
          <Icon name={iconForCategory(destCategory)} size={16} />
        </div>
      </div>
      <div className="absolute bottom-2 left-2 flex items-center gap-1.5 rounded-full bg-white/85 px-2.5 py-1 text-[10px] font-semibold text-ink-3 backdrop-blur">
        <Icon name="mapicon" size={12} /> Kakao Maps 연동 예정
      </div>
    </div>
  );
}

// 키 없으면 경로 요약 텍스트만(폴리라인 지도는 키 필요). 경유지 정보는 상세 배너가 이미 보여준다.
export function RouteMiniMap({ route, destCategory }: { route: RouteResult; destCategory: Category }) {
  if (!HAS_KEY) return <Placeholder destCategory={destCategory} />;
  return <Live route={route} destCategory={destCategory} />;
}
