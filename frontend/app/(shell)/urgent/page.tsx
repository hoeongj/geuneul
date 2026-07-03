"use client";

import { useState } from "react";
import { Icon } from "@/components/ui/Icon";
import { ScenarioButtons } from "@/components/urgent/ScenarioButtons";
import { UrgentResults } from "@/components/urgent/UrgentResults";
import { useGeo } from "@/lib/context/geo";
import { useSelectedPlace } from "@/lib/context/selected";
import { useUrgent } from "@/lib/queries";
import type { Scenario } from "@/types/place";

export default function UrgentPage() {
  const geo = useGeo();
  const selected = useSelectedPlace();
  const [scenario, setScenario] = useState<Scenario | null>(null);
  const query = useUrgent(scenario, { lat: geo.lat, lng: geo.lng });

  return (
    <div className="h-full overflow-y-auto px-4 pt-5 pb-6">
      <header className="mb-4">
        <h1 className="text-[23px] font-extrabold tracking-[-0.4px] text-ink">지금 급해요</h1>
        <p className="mt-1 text-[13px] text-ink-3">상황을 고르면 가장 가까운 곳부터 알려드려요.</p>
      </header>

      <ScenarioButtons selected={scenario} onSelect={setScenario} />

      <div className="mt-5">
        {scenario ? (
          <UrgentResults
            scenario={scenario}
            places={query.data ?? []}
            loading={query.isLoading}
            onSelectPlace={selected.open}
          />
        ) : (
          <div className="flex flex-col items-center gap-2 rounded-[16px] border border-dashed border-line-dashed bg-white/50 px-4 py-8 text-center">
            <span className="text-teal">
              <Icon name="bolt" size={26} />
            </span>
            <p className="text-[13px] text-ink-3">위 버튼을 누르면 가장 가까운 곳을 즉시 찾아드려요.</p>
          </div>
        )}
      </div>
    </div>
  );
}
