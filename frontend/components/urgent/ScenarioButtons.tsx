"use client";

import { Icon } from "@/components/ui/Icon";
import { IconChip } from "@/components/ui/IconChip";
import { SCENARIO_META } from "@/lib/categories";
import type { Scenario } from "@/types/place";

const ORDER: Scenario[] = ["restroom", "rest30", "rain"];

export function ScenarioButtons({
  selected,
  onSelect,
}: {
  selected: Scenario | null;
  onSelect: (s: Scenario) => void;
}) {
  return (
    <div className="flex flex-col gap-2.5">
      {ORDER.map((k) => {
        const meta = SCENARIO_META[k];
        const active = selected === k;
        return (
          <button
            key={k}
            type="button"
            onClick={() => onSelect(k)}
            aria-pressed={active}
            className={
              "flex items-center gap-3 rounded-[16px] border px-3.5 py-3.5 text-left " +
              (active ? "border-forest bg-forest text-cream" : "border-line-cream bg-white text-ink")
            }
          >
            {active ? (
              <IconChip icon={meta.icon} size={46} iconSize={22} tone="ghost" />
            ) : (
              <div className="flex h-[46px] w-[46px] shrink-0 items-center justify-center rounded-[14px] bg-mint text-teal">
                <Icon name={meta.icon} size={22} />
              </div>
            )}
            <div className="min-w-0 flex-1">
              <div className="text-[16px] font-extrabold">{meta.title}</div>
              <div className={active ? "text-[12px] text-cream/80" : "text-[12px] text-ink-3"}>{meta.sub}</div>
            </div>
            <span className={active ? "text-cream/80" : "text-muted-3"}>
              <Icon name="chev" size={18} />
            </span>
          </button>
        );
      })}
    </div>
  );
}
