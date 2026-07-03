"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon } from "@/components/ui/Icon";
import { useSelectedPlace } from "@/lib/context/selected";

const TABS = [
  { href: "/", label: "지도", icon: "mapicon" },
  { href: "/urgent", label: "급해요", icon: "bolt" },
  { href: "/report", label: "제보", icon: "pen" },
] as const;

export function TabBar() {
  const pathname = usePathname();
  const { close } = useSelectedPlace();

  return (
    <nav
      className="flex shrink-0 border-t border-line-white bg-white px-2 pt-2 pb-1.5"
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
