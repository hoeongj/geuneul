"use client";

import dynamic from "next/dynamic";
import { Icon } from "@/components/ui/Icon";
import type { IconName } from "@/lib/icon-paths";

const HAS_KEY = !!process.env.NEXT_PUBLIC_KAKAO_MAP_JS_KEY;

// 키 없을 때 placeholder(디자인 톤 + "연동 예정" 칩).
function Placeholder({ icon }: { icon: IconName }) {
  return (
    <div className="relative h-[150px] w-full overflow-hidden" style={{ background: "var(--color-map-base)" }}>
      <div className="absolute left-[10%] top-[18%] h-16 w-24 rounded-2xl" style={{ background: "var(--color-map-park)" }} />
      <div className="absolute right-[12%] bottom-[16%] h-14 w-20 rounded-2xl" style={{ background: "var(--color-map-block)" }} />
      <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-full text-forest">
        <div className="flex h-8 w-8 items-center justify-center rounded-full border-[2.5px] border-forest bg-white shadow">
          <Icon name={icon} size={16} />
        </div>
      </div>
      <div className="absolute bottom-2 left-2 flex items-center gap-1.5 rounded-full bg-white/85 px-2.5 py-1 text-[10px] font-semibold text-ink-3 backdrop-blur">
        <Icon name="mapicon" size={12} /> Kakao Maps 연동 예정
      </div>
    </div>
  );
}

// 실지도는 SDK(window.kakao) 의존이라 SSR 비활성.
const Live = dynamic(() => import("./DetailMiniMapLive"), {
  ssr: false,
  loading: () => <div className="h-[150px] w-full" style={{ background: "var(--color-map-base)" }} />,
});

export function DetailMiniMap({ lat, lng, icon }: { lat: number; lng: number; icon: IconName }) {
  if (!HAS_KEY) return <Placeholder icon={icon} />;
  return <Live lat={lat} lng={lng} icon={icon} />;
}
