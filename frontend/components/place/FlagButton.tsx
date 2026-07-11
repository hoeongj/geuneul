"use client";

import { useRef, useState } from "react";
import { ApiError, createFlag } from "@/lib/api";
import { useToast } from "@/lib/context/toast";
import { useDialogFocusTrap } from "@/lib/hooks";
import { useMe } from "@/lib/queries";
import { FLAG_REASONS, type FlagReason, type FlagTargetType } from "@/types/flag";

/**
 * 신고 진입점(C1) — 후기/제보 항목의 작은 "신고" 버튼. §9대로 최소·비노출(하단 탭 신설 금지, 눈에 띄지 않는
 * 텍스트 버튼). 비로그인은 백엔드까지 안 가고 토스트로 안내하고, 로그인 사용자만 사유(라디오)+선택 코멘트
 * 시트로 POST /flags. 모더레이션이 주인공이 되지 않게 톤·표면을 절제한다.
 */
export function FlagButton({
  targetType,
  targetId,
  className,
}: {
  targetType: FlagTargetType;
  targetId: number;
  className?: string;
}) {
  const { data: me } = useMe();
  const { show } = useToast();
  const [open, setOpen] = useState(false);

  const onTrigger = () => {
    if (!me) {
      show("로그인이 필요해요");
      return;
    }
    setOpen(true);
  };

  return (
    <>
      <button
        type="button"
        onClick={onTrigger}
        aria-haspopup="dialog"
        className={className ?? "text-[11px] text-muted-3 underline-offset-2 active:underline lg:hover:text-muted"}
      >
        신고
      </button>
      {open && <FlagSheet targetType={targetType} targetId={targetId} onClose={() => setOpen(false)} />}
    </>
  );
}

// 신고 사유 시트 — 라디오 사유 + 선택 코멘트(≤500). fixed 모달(스크롤 컨테이너 깊숙이서 열려도 뷰포트 기준).
function FlagSheet({
  targetType,
  targetId,
  onClose,
}: {
  targetType: FlagTargetType;
  targetId: number;
  onClose: () => void;
}) {
  const { show } = useToast();
  const [reason, setReason] = useState<FlagReason>("SPAM");
  const [detail, setDetail] = useState("");
  const [pending, setPending] = useState(false);
  const sheetRef = useRef<HTMLDivElement>(null);
  // 진짜 모달(fixed + backdrop)이라 Tab 트랩 + Esc + 포커스 복귀를 켠다(C2 공유 훅 재사용).
  useDialogFocusTrap(sheetRef, true, onClose, { trapTab: true });

  const submit = async () => {
    if (pending) return;
    setPending(true);
    try {
      await createFlag({ targetType, targetId, reason, detail: detail.trim() || undefined });
      show("신고가 접수됐어요");
      onClose();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) show("이미 신고한 항목이에요");
      else if (err instanceof ApiError && err.status === 401) show("로그인이 필요해요");
      else if (err instanceof ApiError && err.status === 404) show("이미 사라진 항목이에요");
      else show("신고에 실패했어요 · 잠시 후 다시 시도해 주세요");
      setPending(false); // 성공 시엔 onClose로 언마운트되므로 이 경로에서만 해제
    }
  };

  return (
    <div className="fixed inset-0 z-[60] flex flex-col justify-end" role="dialog" aria-modal="true" aria-label="신고하기">
      <button type="button" className="flex-1 bg-black/30" onClick={onClose} aria-label="닫기" />
      <div
        ref={sheetRef}
        tabIndex={-1}
        className="gn-overlay rounded-t-[22px] bg-white p-4 shadow-sheet focus:outline-none lg:mx-auto lg:mb-6 lg:max-w-[420px] lg:rounded-[22px]"
      >
        <div className="mb-2 flex w-full justify-center">
          <span className="h-[5px] w-[38px] rounded-full" style={{ background: "var(--color-sheet-handle)" }} />
        </div>
        <h2 className="mb-1 text-[16px] font-extrabold text-ink">신고하기</h2>
        <p className="mb-3 text-[12px] text-muted">부적절한 이유를 알려주세요. 관리자가 확인해요.</p>

        <fieldset className="mb-3 flex flex-col gap-1.5">
          <legend className="sr-only">신고 사유</legend>
          {FLAG_REASONS.map((r) => (
            <label
              key={r.value}
              className={
                "flex cursor-pointer items-center gap-2.5 rounded-[12px] border px-3.5 py-3 text-[14px] " +
                (reason === r.value ? "border-teal bg-mint-3 text-teal-deep" : "border-line-cream bg-white text-ink-2")
              }
            >
              <input
                type="radio"
                name="flag-reason"
                value={r.value}
                checked={reason === r.value}
                onChange={() => setReason(r.value)}
                className="accent-teal"
              />
              {r.label}
            </label>
          ))}
        </fieldset>

        <textarea
          value={detail}
          onChange={(e) => setDetail(e.target.value.slice(0, 500))}
          placeholder="자세한 내용 (선택, 500자 이내)"
          aria-label="신고 상세"
          rows={2}
          maxLength={500}
          className="mb-3 w-full resize-none rounded-[10px] border border-line-cream bg-white px-3 py-2 text-[13px] text-ink placeholder:text-muted-2 focus:border-teal focus:outline-none"
        />

        <div className="flex gap-2.5">
          <button
            type="button"
            onClick={onClose}
            className="h-[48px] flex-1 rounded-[14px] border border-line-cream bg-white text-[14px] font-bold text-muted"
          >
            취소
          </button>
          <button
            type="button"
            onClick={submit}
            disabled={pending}
            className="h-[48px] flex-1 rounded-[14px] bg-forest text-[14px] font-bold text-cream disabled:opacity-60"
          >
            {pending ? "접수 중…" : "신고 접수"}
          </button>
        </div>
      </div>
    </div>
  );
}
