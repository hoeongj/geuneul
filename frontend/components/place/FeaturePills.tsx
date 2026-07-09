import { Icon } from "@/components/ui/Icon";
import { FEATURE_META } from "@/lib/categories";
import type { PlaceFeature } from "@/types/place";

// 등급화된 시설 속성(ADR-0005 §④)을 pill 로 렌더. label 은 백엔드가 등급을 반영해 만든 표시 문구
// (예 "콘센트 많음"), polarity 로 칩 색을 정한다. 백엔드가 상세에서만 채우므로 목록/마커엔 없음.
export function FeaturePills({ features }: { features?: PlaceFeature[] }) {
  if (!features || features.length === 0) {
    return <p className="text-[12.5px] text-muted">시설 정보는 준비 중이에요.</p>;
  }
  return (
    <div className="flex flex-wrap gap-2">
      {features.map((f) => {
        const icon = FEATURE_META[f.type]?.icon ?? "dots";
        const tone =
          f.polarity === "NEGATIVE"
            ? "bg-red-50 text-red-600"
            : f.polarity === "NEUTRAL"
              ? "bg-line-cream text-ink-3"
              : "bg-mint-2 text-teal-deep";
        return (
          <span
            key={`${f.type}:${f.level}`}
            className={
              "inline-flex items-center gap-1.5 rounded-full px-3 py-[7px] text-[12.5px] font-bold " + tone
            }
          >
            <Icon name={icon} size={15} />
            {f.label}
          </span>
        );
      })}
    </div>
  );
}
