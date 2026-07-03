import { Icon } from "@/components/ui/Icon";
import { FEATURE_META } from "@/lib/categories";
import type { FeatureType } from "@/types/place";

// place_features → 시설 pill. 현재 백엔드가 features 를 미노출(P2/P3)이라 없으면 준비중 문구.
export function FeaturePills({ features }: { features?: FeatureType[] }) {
  if (!features || features.length === 0) {
    return <p className="text-[12.5px] text-muted">시설 정보는 준비 중이에요.</p>;
  }
  return (
    <div className="flex flex-wrap gap-2">
      {features.map((f) => {
        const meta = FEATURE_META[f];
        if (!meta) return null;
        return (
          <span
            key={f}
            className="inline-flex items-center gap-1.5 rounded-full bg-mint-2 px-3 py-[7px] text-[12.5px] font-bold text-teal-deep"
          >
            <Icon name={meta.icon} size={15} />
            {meta.label}
          </span>
        );
      })}
    </div>
  );
}
