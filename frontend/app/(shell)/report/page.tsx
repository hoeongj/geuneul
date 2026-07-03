"use client";

import { useState } from "react";
import { Icon } from "@/components/ui/Icon";
import { useToast } from "@/lib/context/toast";

// report_type(휘발성 제보) 6종. 실제 POST /reports·사진 업로드·로그인은 P2 — 여기선 레이아웃/선택 preview.
const STATUSES = [
  { k: "COOL", emoji: "🧊", label: "시원해요" },
  { k: "HOT", emoji: "🥵", label: "더워요" },
  { k: "BUG", emoji: "🐛", label: "벌레 많아요" },
  { k: "RESTROOM_CLEAN", emoji: "🚻", label: "화장실 깨끗" },
  { k: "WATER_OK", emoji: "💧", label: "물 있어요" },
  { k: "FLOOD", emoji: "🌊", label: "침수됐어요" },
] as const;

export default function ReportPage() {
  const { show } = useToast();
  const [status, setStatus] = useState<string | null>(null);
  const [comment, setComment] = useState("");
  const [anon, setAnon] = useState(true);
  const [done, setDone] = useState(false);

  const submitLabel = done ? "고마워요! 제보 완료 (미리보기)" : status ? "제보 보내기" : "지금 상태를 골라주세요";

  const onSubmit = () => {
    if (!status || done) return;
    // 실제 전송은 P2. 미리보기에서는 완료 상태만 표시.
    setDone(true);
    show("고마워요! 제보 완료 (미리보기)");
  };

  return (
    <div className="h-full overflow-y-auto px-4 pt-5 pb-6">
      <header className="mb-4">
        <div className="flex items-center gap-2">
          <h1 className="text-[23px] font-extrabold tracking-[-0.4px] text-ink">제보하기</h1>
          <span className="rounded-full bg-mint px-2 py-0.5 text-[10px] font-bold text-teal-deep">P2 미리보기</span>
        </div>
        <p className="mt-1 text-[13px] text-ink-3">한 탭이면 끝. 지금 상태를 알려주세요.</p>
      </header>

      {/* 장소 컨텍스트 */}
      <div className="mb-4 flex items-center gap-3 rounded-[14px] border border-line-cream bg-white px-3.5 py-3">
        <div className="flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-[13px] bg-mint text-forest">
          <Icon name="locate" size={20} />
        </div>
        <div className="min-w-0 flex-1">
          <div className="text-[11px] text-muted">제보할 장소</div>
          <div className="truncate text-[15px] font-bold text-ink">현재 위치 주변</div>
        </div>
        <button
          type="button"
          onClick={() => show("장소 선택은 P2에서 제공돼요")}
          className="shrink-0 text-[13px] font-bold text-teal"
        >
          변경
        </button>
      </div>

      {/* 상태 이모지 그리드 */}
      <div className="mb-4 grid grid-cols-3 gap-2.5">
        {STATUSES.map((s) => {
          const active = status === s.k;
          return (
            <button
              key={s.k}
              type="button"
              onClick={() => {
                setStatus(s.k);
                setDone(false);
              }}
              aria-pressed={active}
              className={
                "flex flex-col items-center gap-1.5 rounded-[16px] border py-4 " +
                (active ? "border-teal bg-mint-3 text-teal-deep" : "border-line-cream bg-white text-ink-2")
              }
            >
              <span className="text-[26px] leading-none">{s.emoji}</span>
              <span className="text-[12px] font-bold">{s.label}</span>
            </button>
          );
        })}
      </div>

      {/* 사진 + 코멘트 */}
      <div className="mb-4 flex gap-3">
        <button
          type="button"
          onClick={() => show("사진 첨부는 P2에서 제공돼요")}
          className="flex h-[76px] w-[76px] shrink-0 flex-col items-center justify-center gap-1 rounded-[14px] border border-dashed border-line-dashed bg-white text-muted"
          aria-label="사진 추가"
        >
          <Icon name="locate" size={20} />
          <span className="text-[10px] font-semibold">사진</span>
        </button>
        <input
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="한 줄 코멘트 (선택)"
          className="h-[76px] flex-1 rounded-[14px] border border-line-cream bg-white px-3.5 text-[14px] text-ink placeholder:text-muted-2 focus:border-teal focus:outline-none"
        />
      </div>

      {/* 익명 토글 */}
      <div className="mb-5 flex items-center justify-between rounded-[14px] border border-line-cream bg-white px-3.5 py-3">
        <div className="min-w-0">
          <div className="text-[14px] font-bold text-ink">익명으로 제보</div>
          <div className="text-[11px] text-muted">로그인하면 신뢰도 배지가 붙어요 · P2</div>
        </div>
        <button
          type="button"
          role="switch"
          aria-checked={anon}
          onClick={() => setAnon((v) => !v)}
          className="relative h-7 w-12 shrink-0 rounded-full transition-colors"
          style={{ background: anon ? "var(--color-teal)" : "#D6D3C7" }}
        >
          <span
            className="absolute top-0.5 h-6 w-6 rounded-full bg-white shadow transition-transform"
            style={{ left: 2, transform: anon ? "translateX(20px)" : "translateX(0)" }}
          />
        </button>
      </div>

      {/* 제출 */}
      <button
        type="button"
        onClick={onSubmit}
        disabled={!status || done}
        className="h-[52px] w-full rounded-[14px] text-[15px] font-bold text-cream disabled:text-ink-3"
        style={{ background: status && !done ? "var(--color-forest)" : done ? "var(--color-teal)" : "#DED9CC" }}
      >
        {submitLabel}
      </button>
      <p className="mt-2.5 text-center text-[11px] text-muted">휘발성 제보예요 · 시간이 지나면 자동으로 사라져요</p>
    </div>
  );
}
