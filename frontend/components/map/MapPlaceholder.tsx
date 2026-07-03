import { Icon } from "@/components/ui/Icon";

// Kakao JS 키가 없거나 SDK 로딩 실패 시의 지도 자리. 데이터(리스트)는 그대로 동작하고
// 여기만 실지도로 교체되면 된다. 디자인 placeholder 톤(민트/공원/물/블록)으로 은은하게.
export function MapPlaceholder({ reason = "no-key" }: { reason?: "no-key" | "error" | "loading" }) {
  const text =
    reason === "error"
      ? "지도를 불러오지 못했어요"
      : reason === "loading"
        ? "지도를 불러오는 중…"
        : "Kakao Maps 연동 예정 · 지도 키 필요";
  return (
    <div className="relative h-full w-full overflow-hidden" style={{ background: "var(--color-map-base)" }}>
      {/* 공원/물/블록 블록으로 지도 느낌만 */}
      <div className="absolute left-[8%] top-[12%] h-32 w-40 rounded-3xl" style={{ background: "var(--color-map-park)" }} />
      <div className="absolute right-[6%] top-[30%] h-40 w-36 rounded-3xl" style={{ background: "var(--color-map-water)" }} />
      <div className="absolute left-[14%] bottom-[20%] h-28 w-44 rounded-3xl" style={{ background: "var(--color-map-block)" }} />
      <div className="absolute right-[16%] bottom-[10%] h-24 w-28 rounded-2xl" style={{ background: "var(--color-map-park)" }} />
      <div
        className="absolute inset-x-0 top-1/2 h-6 -translate-y-1/2"
        style={{ background: "var(--color-map-road)" }}
      />
      <div className="absolute inset-x-0 bottom-[34%] flex justify-center">
        <div className="flex items-center gap-2 rounded-full bg-white/85 px-3.5 py-2 text-[11px] font-semibold text-ink-3 shadow-search backdrop-blur">
          <span className="text-status">
            <Icon name="mapicon" size={15} />
          </span>
          {text}
        </div>
      </div>
    </div>
  );
}
