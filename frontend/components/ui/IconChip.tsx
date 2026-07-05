import { Icon } from "./Icon";
import type { IconName } from "@/lib/icon-paths";

// 민트 배경 아이콘 사각칩(리스트 로우 42 / 상세·급해요 46). 아이콘은 forest.
export function IconChip({
  icon,
  size = 42,
  iconSize = 20,
  tone = "mint",
}: {
  icon: IconName;
  size?: number;
  iconSize?: number;
  tone?: "mint" | "ghost";
}) {
  const radius = size >= 46 ? 14 : 13;
  const bg = tone === "ghost" ? "rgba(255,255,255,.15)" : undefined;
  return (
    <div
      className={
        "flex shrink-0 items-center justify-center " +
        (tone === "mint" ? "bg-mint text-forest" : "text-white")
      }
      style={{ width: size, height: size, borderRadius: radius, background: bg }}
    >
      <Icon name={icon} size={iconSize} />
    </div>
  );
}
