"use client";

import { Icon } from "@/components/ui/Icon";
import { CATEGORY_META, FILTER_CATEGORIES } from "@/lib/categories";
import type { IconName } from "@/lib/icon-paths";
import type { Category } from "@/types/place";

interface FilterChipsProps {
  selected: Category[];
  onToggle: (cat: Category) => void;
  onClear: () => void;
}

function Chip({
  active,
  icon,
  label,
  onClick,
}: {
  active: boolean;
  icon: IconName;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={
        "flex h-9 shrink-0 items-center gap-1.5 rounded-full border px-3.5 text-[13px] font-bold transition-colors " +
        (active
          ? "border-forest bg-forest text-cream lg:hover:bg-forest/90"
          : "border-line-chip bg-white text-ink-2 lg:hover:border-forest/40 lg:hover:bg-mint-3/50")
      }
    >
      <Icon name={icon} size={16} />
      {label}
    </button>
  );
}

// 다중 선택. 맨 앞 "전체" = 해제(선택 없음일 때 활성). 이어서 카테고리 7종.
export function FilterChips({ selected, onToggle, onClear }: FilterChipsProps) {
  return (
    <div className="no-scrollbar flex gap-[7px] overflow-x-auto">
      <Chip active={selected.length === 0} icon="dots" label="전체" onClick={onClear} />
      {FILTER_CATEGORIES.map((cat) => (
        <Chip
          key={cat}
          active={selected.includes(cat)}
          icon={CATEGORY_META[cat].icon}
          label={CATEGORY_META[cat].label}
          onClick={() => onToggle(cat)}
        />
      ))}
    </div>
  );
}
