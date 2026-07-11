"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon } from "@/components/ui/Icon";
import { TABS } from "@/components/shell/TabBar";
import { useSelectedPlace } from "@/lib/context/selected";

// 데스크톱(≥lg) 전용 좌측 내비게이션 레일 — 모바일 하단 3탭(TabBar)을 세로 레일로.
// 탭 항목은 TabBar와 단일 소스(TABS) 공유. 상단 브랜드 마크(그늘=나무 그늘)는 홈으로.
export function NavRail({ className = "" }: { className?: string }) {
  const pathname = usePathname();
  const { close } = useSelectedPlace();

  return (
    <nav className={"w-[76px] shrink-0 flex-col items-center gap-1 border-r border-line-cream bg-white pt-4 " + className}>
      <Link
        href="/"
        onClick={close}
        aria-label="그늘 홈"
        className="mb-3 flex h-11 w-11 items-center justify-center rounded-[14px] bg-forest text-cream"
      >
        <Icon name="tree" size={22} />
      </Link>

      {TABS.map((tab) => {
        const active = pathname === tab.href;
        return (
          <Link
            key={tab.href}
            href={tab.href}
            onClick={close}
            aria-current={active ? "page" : undefined}
            className="flex w-full flex-col items-center gap-1 py-2.5"
          >
            <span className={active ? "text-forest" : "text-muted-3"}>
              <Icon name={tab.icon} size={24} strokeWidth={active ? 2 : 1.8} />
            </span>
            <span
              className={active ? "text-[10.5px] font-extrabold text-forest" : "text-[10.5px] font-semibold text-muted-3"}
            >
              {tab.label}
            </span>
          </Link>
        );
      })}
    </nav>
  );
}
