"use client";

import { Icon } from "@/components/ui/Icon";
import { IconChip } from "@/components/ui/IconChip";
import { categoryLabel, iconForCategory, SCENARIO_META } from "@/lib/categories";
import { formatDistance, walkMinutes } from "@/lib/geo";
import { kakaoDirectionsUrl } from "@/lib/kakao";
import type { Place, Scenario } from "@/types/place";

function HeroCard({ place, onOpen }: { place: Place; onOpen: () => void }) {
  const walk = walkMinutes(place.distanceM);
  const dist = formatDistance(place.distanceM);
  return (
    <div className="rounded-[18px] bg-forest p-4 text-cream shadow-hero">
      <button type="button" onClick={onOpen} className="w-full text-left">
        <div className="mb-2 flex items-center gap-2">
          <span className="rounded-full bg-teal-fill px-2 py-0.5 text-[11px] font-extrabold text-white">1위</span>
          {walk && <span className="text-[12px] text-cream/85">도보 약 {walk}분</span>}
        </div>
        <div className="text-[19px] font-extrabold">{place.name}</div>
        <div className="mt-0.5 text-[12.5px] text-cream/80">
          {categoryLabel(place.category, place.categoryLabel)} · {place.address}
        </div>
        {place.reason && <div className="mt-1.5 text-[12px] font-bold text-mint-3">{place.reason}</div>}
      </button>
      <div className="mt-3 flex items-center justify-between">
        {dist && <span className="text-[13px] font-bold text-cream/90">{dist}</span>}
        <a
          href={kakaoDirectionsUrl(place)}
          target="_blank"
          rel="noreferrer"
          className="flex items-center gap-1.5 rounded-full bg-mint-3 px-3.5 py-2 text-[13px] font-bold text-teal-deep"
        >
          <Icon name="nav" size={15} />
          길찾기
        </a>
      </div>
    </div>
  );
}

function RankRow({ place, rank, onOpen }: { place: Place; rank: number; onOpen: () => void }) {
  const walk = walkMinutes(place.distanceM);
  const dist = formatDistance(place.distanceM);
  return (
    <button
      type="button"
      onClick={onOpen}
      className="flex w-full items-center gap-3 rounded-[14px] border border-line-cream bg-white px-3 py-3 text-left"
    >
      <span className="w-4 shrink-0 text-center text-[14px] font-extrabold text-muted">{rank}</span>
      <IconChip icon={iconForCategory(place.category)} />
      <div className="min-w-0 flex-1">
        <div className="truncate text-[15px] font-bold text-ink">{place.name}</div>
        <div className="truncate text-[12px] text-ink-3">
          {categoryLabel(place.category, place.categoryLabel)}
          {walk ? ` · 도보 ${walk}분` : ""}
          {place.reason ? ` · ${place.reason}` : ""}
        </div>
      </div>
      {dist && <span className="shrink-0 text-[14px] font-extrabold text-teal">{dist}</span>}
    </button>
  );
}

export function UrgentResults({
  scenario,
  places,
  loading,
  onSelectPlace,
}: {
  scenario: Scenario;
  places: Place[];
  loading: boolean;
  onSelectPlace: (place: Place) => void;
}) {
  if (loading && places.length === 0) {
    return <div className="py-10 text-center text-[13px] text-muted">가까운 곳을 찾는 중…</div>;
  }
  if (places.length === 0) {
    return <div className="py-10 text-center text-[13px] text-muted">이 근처에서 마땅한 곳을 못 찾았어요.</div>;
  }
  const [first, ...rest] = places;
  return (
    <div className="flex flex-col gap-3">
      <h2 className="text-[14px] font-extrabold text-ink-2">{SCENARIO_META[scenario].resultTitle}</h2>
      <HeroCard place={first} onOpen={() => onSelectPlace(first)} />
      {rest.map((p, i) => (
        <RankRow key={p.id} place={p} rank={i + 2} onOpen={() => onSelectPlace(p)} />
      ))}
    </div>
  );
}
