"use client";

import { useState } from "react";
import { Icon } from "@/components/ui/Icon";
import type { SurgeInfo } from "@/types/alert";

interface SurgeBannerProps {
  surges: SurgeInfo[];
  onSelect: (placeId: number) => void;
}

// 뷰포트 내 제보 급증 배너(A4). 표현 규율(§6): 백엔드가 순화한 중립 message를 그대로 쓰고, 색도 경고 적색이
// 아니라 따뜻한 앰버로 "주의" 정도만 준다(공포 조장 금지). 간판(지도)을 가리지 않게 상단에 얇게 얹고 접을 수 있다.
export function SurgeBanner({ surges, onSelect }: SurgeBannerProps) {
  // 접힘은 "어떤 급증 집합을 접었는지"(signature)로 관리한다 — effect 없이 순수 파생. 새 급증이 와서
  // signature가 바뀌면 자동으로 다시 보인다(접었던 상황과 다른 상황이므로).
  const [dismissedSig, setDismissedSig] = useState<string | null>(null);
  const signature = surges.map((s) => s.placeId).sort((a, b) => a - b).join(",");

  if (surges.length === 0 || signature === dismissedSig) return null;

  const head = surges[0];
  const rest = surges.length - 1;

  return (
    <div className="pointer-events-auto flex items-center gap-2.5 rounded-2xl border border-[#F0D9B5] bg-[#FFF6E9] px-3.5 py-2.5 shadow-sm">
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[#F6C877] text-[#7A4E00]">
        <Icon name="bolt" size={16} />
      </span>
      <button
        type="button"
        onClick={() => onSelect(head.placeId)}
        className="min-w-0 flex-1 text-left"
      >
        <p className="truncate text-[13px] font-bold text-[#7A4E00]">{head.message}</p>
        <p className="truncate text-[12px] text-[#9A6B14]">
          {head.name}
          {rest > 0 ? ` 외 ${rest}곳` : ""}
        </p>
      </button>
      <button
        type="button"
        onClick={() => setDismissedSig(signature)}
        aria-label="급증 알림 접기"
        className="shrink-0 rounded-full p-1 text-[#9A6B14]"
      >
        <Icon name="chev" size={18} />
      </button>
    </div>
  );
}
