"use client";

import { Icon } from "@/components/ui/Icon";
import { IconChip } from "@/components/ui/IconChip";
import { categoryLabel, iconForCategory } from "@/lib/categories";
import { useGeo } from "@/lib/context/geo";
import { useSelectedPlace } from "@/lib/context/selected";
import { useToast } from "@/lib/context/toast";
import { formatDistance, haversineMeters, walkMinutes } from "@/lib/geo";
import { kakaoDirectionsUrl, kakaoMapUrl } from "@/lib/kakao";
import { usePlace, usePlaceReports } from "@/lib/queries";
import { formatRelativeTime, REPORT_META } from "@/lib/reports";
import { GRADE_META, gradeOf } from "@/lib/survival";
import type { Place } from "@/types/place";
import { DetailMiniMap } from "./DetailMiniMap";
import { FeaturePills } from "./FeaturePills";
import { ReviewsSection } from "./ReviewsSection";

// 예약 섹션(P2/P3): 흐리게 자리만.
function ReservedBlock({ title, badge, children }: { title: string; badge: string; children: React.ReactNode }) {
  return (
    <section className="rounded-[14px] border border-line-cream bg-white/60 p-3.5 opacity-70">
      <div className="mb-2 flex items-center gap-2">
        <h3 className="text-[14px] font-extrabold text-ink-2">{title}</h3>
        <span className="rounded-full bg-mint px-2 py-0.5 text-[10px] font-bold text-teal-deep">{badge}</span>
      </div>
      {children}
    </section>
  );
}

// 최근 제보(라이브) — 유효(미만료) 제보 최신순. 이 제보들이 위 "지금 상태" survival_score로 집계된다(ADR-0007).
function RecentReports({ placeId }: { placeId: number }) {
  const { data, isLoading, isError } = usePlaceReports(placeId);
  return (
    <section className="rounded-[14px] border border-line-cream bg-white p-3.5">
      <div className="mb-2 flex items-center gap-2">
        <h3 className="text-[14px] font-extrabold text-ink-2">최근 제보</h3>
        <span className="rounded-full bg-mint-3 px-2 py-0.5 text-[10px] font-bold text-teal-deep">실시간</span>
      </div>
      {isLoading ? (
        <p className="py-1 text-[12.5px] text-muted">최근 제보를 불러오는 중…</p>
      ) : isError ? (
        // 에러를 "제보 없음"으로 뭉뚱그리지 않는다 — 네트워크 실패와 실제 무제보는 다른 상태.
        <p className="py-1 text-[12.5px] text-muted">최근 제보를 불러오지 못했어요</p>
      ) : !data || data.length === 0 ? (
        <p className="py-1 text-[12.5px] text-muted">아직 제보가 없어요 · 제보 탭에서 첫 제보를 남겨보세요</p>
      ) : (
        <ul className="flex flex-col gap-2.5">
          {data.map((r) => (
            <li key={r.id} className="flex items-start gap-2.5">
              <span className="text-[18px] leading-none">{REPORT_META[r.reportType]?.emoji ?? "📍"}</span>
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline gap-2">
                  <span className="text-[13px] font-bold text-ink">{r.reportTypeLabel}</span>
                  <span className="text-[11px] text-muted">{formatRelativeTime(r.createdAt)}</span>
                  {r.anonymous && <span className="text-[10px] text-muted-3">익명</span>}
                </div>
                {r.comment && <p className="truncate text-[12.5px] text-ink-3">{r.comment}</p>}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

export function PlaceDetailOverlay() {
  const { id, seed, close } = useSelectedPlace();
  const geo = useGeo();
  const { show } = useToast();
  const query = usePlace(id);

  if (id == null) return null;

  const place: Place | null = query.data ?? seed ?? null;
  const distanceM =
    seed?.distanceM ??
    (place ? haversineMeters({ lat: geo.lat, lng: geo.lng }, { lat: place.lat, lng: place.lng }) : null);
  const dist = formatDistance(distanceM);
  const walk = walkMinutes(distanceM);

  const kakaoLink = place ? kakaoDirectionsUrl(place) : "#";

  const onShare = async () => {
    if (!place) return;
    const url = kakaoMapUrl(place);
    try {
      if (typeof navigator !== "undefined" && navigator.share) {
        await navigator.share({ title: place.name, text: `${place.name} · 그늘`, url });
      } else if (typeof navigator !== "undefined" && navigator.clipboard) {
        await navigator.clipboard.writeText(url);
        show("링크를 복사했어요");
      }
    } catch {
      /* 사용자가 공유 취소 */
    }
  };

  return (
    <div className="gn-overlay absolute inset-0 z-40 flex flex-col overflow-y-auto bg-cream">
      {/* 헤더 */}
      <header className="sticky top-0 z-10 flex items-center justify-between border-b border-line-cream bg-cream px-2 py-2">
        <button type="button" onClick={close} aria-label="뒤로" className="flex h-[38px] w-[38px] items-center justify-center text-ink">
          <Icon name="chevLeft" size={22} />
        </button>
        <h1 className="text-[15px] font-extrabold text-ink">장소 상세</h1>
        <button type="button" onClick={onShare} aria-label="공유" className="flex h-[38px] w-[38px] items-center justify-center text-ink">
          <Icon name="share" size={19} />
        </button>
      </header>

      {/* 미니맵: 선택 장소 중심(키 있으면 실지도, 없으면 placeholder) */}
      {place ? (
        <DetailMiniMap lat={place.lat} lng={place.lng} icon={iconForCategory(place.category)} />
      ) : (
        <div className="h-[150px] w-full" style={{ background: "var(--color-map-base)" }} />
      )}

      <div className="flex flex-col gap-4 px-4 py-4">
        {/* 타이틀 */}
        <div className="flex items-center gap-3">
          <IconChip icon={place ? iconForCategory(place.category) : "dots"} size={46} iconSize={22} />
          <div className="min-w-0">
            <h2 className="truncate text-[20px] font-extrabold text-ink">{place?.name ?? "불러오는 중…"}</h2>
            <p className="text-[13px] text-ink-3">{place ? categoryLabel(place.category, place.categoryLabel) : ""}</p>
          </div>
        </div>

        {/* 지금 상태 — survival_score 등급(ADR-0007). 유효 제보 기준 3색. */}
        {(() => {
          const grade = gradeOf(place ?? {});
          const meta = GRADE_META[grade];
          const s = place?.survival ?? null;
          return (
            <div className="flex items-center justify-between rounded-[12px] border border-line-cream bg-white px-3.5 py-3">
              <span className="flex items-center gap-2 text-[13px] font-semibold" style={{ color: meta.color }}>
                <span className="h-2 w-2 rounded-full" style={{ background: meta.color }} />
                지금 상태 · {meta.label}
                {grade !== "UNKNOWN" && s && <span className="font-extrabold">{s.score}</span>}
              </span>
              <span className="text-[11px] text-muted">
                {grade === "UNKNOWN"
                  ? "제보 쌓이면 표시"
                  : `최근 제보 ${s?.reportCount ?? 0}건 반영`}
              </span>
            </div>
          );
        })()}

        {/* 주소 / 거리·도보 */}
        <div className="flex flex-col gap-2.5">
          <div className="flex items-start gap-2.5 text-[14px] text-ink-2">
            <span className="mt-0.5 shrink-0 text-teal">
              <Icon name="locate" size={17} />
            </span>
            <span>{place?.address ?? "—"}</span>
          </div>
          {dist && (
            <div className="flex items-center gap-2.5 text-[14px] text-ink-2">
              <span className="shrink-0 text-teal">
                <Icon name="nav" size={17} />
              </span>
              <span>
                {dist} · 도보 약 {walk}분
              </span>
            </div>
          )}
        </div>

        {/* 시설 */}
        <div>
          <h3 className="mb-2 text-[14px] font-extrabold text-ink-2">시설</h3>
          <FeaturePills features={place?.features} />
        </div>

        {/* 액션 */}
        <div className="flex gap-2.5">
          <a
            href={kakaoLink}
            target="_blank"
            rel="noreferrer"
            onClick={() => show("카카오맵으로 길찾기를 엽니다")}
            className="flex h-[52px] flex-1 items-center justify-center gap-2 rounded-[14px] bg-forest text-[15px] font-bold text-cream"
          >
            <Icon name="nav" size={18} />
            카카오맵 길찾기
          </a>
          <button
            type="button"
            onClick={onShare}
            aria-label="공유"
            className="flex h-[52px] w-[52px] items-center justify-center rounded-[14px] bg-mint text-forest"
          >
            <Icon name="share" size={20} />
          </button>
        </div>

        {/* 예약 섹션(P2/P3) */}
        <div className="mt-1 flex flex-col gap-3">
          <ReservedBlock title="AI 한 줄 요약" badge="P3">
            <p className="text-[13px] italic text-muted">“최근 제보가 쌓이면 이곳의 지금 상태를 한 줄로 요약해 드려요.”</p>
          </ReservedBlock>
          {id != null && <RecentReports placeId={id} />}
          {id != null && <ReviewsSection placeId={id} />}
        </div>
      </div>
    </div>
  );
}
