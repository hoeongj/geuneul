"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon } from "@/components/ui/Icon";
import { useSelectedPlace } from "@/lib/context/selected";

export const TABS = [
  { href: "/", label: "지도", icon: "mapicon" },
  { href: "/urgent", label: "급해요", icon: "bolt" },
  { href: "/report", label: "제보", icon: "pen" },
  { href: "/mypage", label: "내 정보", icon: "user" },
] as const;

export function TabBar({ className = "" }: { className?: string }) {
  const pathname = usePathname();
  const { close } = useSelectedPlace();

  return (
    <nav
      aria-label="주 메뉴"
      className={"flex shrink-0 border-t border-line-white bg-white px-2 pt-2 pb-1.5 " + className}
      style={{ paddingBottom: "max(6px, env(safe-area-inset-bottom))" }}
    >
      {TABS.map((tab) => {
        const active = pathname === tab.href;
        return (
          <Link
            key={tab.href}
            href={tab.href}
            onClick={close}
            aria-current={active ? "page" : undefined}
            className="flex min-h-[44px] flex-1 flex-col items-center justify-center gap-1"
          >
            <span className={active ? "text-forest" : "text-muted-3"}>
              <Icon name={tab.icon} size={23} strokeWidth={active ? 2 : 1.8} />
            </span>
            <span
              className={active ? "text-[11px] font-extrabold text-forest" : "text-[11px] font-semibold text-muted-3"}
            >
              {tab.label}
            </span>
          </Link>
        );
      })}
    </nav>
  );
}
