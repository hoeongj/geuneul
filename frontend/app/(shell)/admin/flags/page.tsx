"use client";

import Link from "next/link";
import { useState } from "react";
import { Icon } from "@/components/ui/Icon";
import { useToast } from "@/lib/context/toast";
import { useAdminFlags, useMe, useResolveFlag } from "@/lib/queries";
import { formatRelativeTime } from "@/lib/reports";
import { FLAG_REASON_LABEL, type AdminFlagItem, type FlagStatus } from "@/types/flag";

const STATUS_TABS: { value: FlagStatus; label: string }[] = [
  { value: "PENDING", label: "대기중" },
  { value: "RESOLVED", label: "숨김 처리" },
  { value: "DISMISSED", label: "반려" },
];

const TARGET_LABEL: Record<string, string> = { REPORT: "제보", REVIEW: "후기" };

/**
 * 관리자 검수 큐(C1) — ADMIN 전용. 신고 목록을 대상 요약과 함께 보고 RESOLVED(대상 숨김)/DISMISSED 처리.
 * 클라 role 게이트는 UX용이고 실제 방어는 백엔드(/admin/** = hasRole ADMIN). §9대로 별도 하단 탭 없이
 * 마이페이지의 조건부 링크로만 진입한다. 데스크톱은 mypage와 동일한 가독 폭(lg:max-w-720).
 */
export default function AdminFlagsPage() {
  const { data: me, isLoading: meLoading } = useMe();
  const [status, setStatus] = useState<FlagStatus>("PENDING");
  const [page, setPage] = useState(0);
  const isAdmin = me?.role === "ADMIN";
  const { data, isLoading, isError } = useAdminFlags(status, page, isAdmin);

  const selectStatus = (next: FlagStatus) => {
    setStatus(next);
    setPage(0); // 탭 전환 시 항상 첫 페이지부터
  };

  return (
    <div className="relative h-full overflow-y-auto px-4 pt-5 pb-6 lg:mx-auto lg:max-w-[720px] lg:px-8 lg:pt-8">
      <header className="mb-4 flex items-center gap-2">
        <Link
          href="/mypage"
          aria-label="뒤로"
          className="flex h-[38px] w-[38px] items-center justify-center rounded-full text-ink transition-colors lg:hover:bg-line-cream"
        >
          <Icon name="chevLeft" size={22} />
        </Link>
        <div>
          <h1 className="text-[21px] font-extrabold tracking-[-0.4px] text-ink">신고 검수 큐</h1>
          <p className="mt-0.5 text-[12.5px] text-ink-3">관리자 전용 · 허위·부적절 제보/후기 처리</p>
        </div>
      </header>

      {meLoading ? (
        <div className="py-10 text-center text-[13px] text-muted">불러오는 중…</div>
      ) : !me ? (
        <GateMessage>
          로그인이 필요해요.{" "}
          <Link href="/mypage" className="font-bold text-teal underline-offset-2 active:underline">
            로그인하러 가기
          </Link>
        </GateMessage>
      ) : !isAdmin ? (
        <GateMessage>관리자 권한이 필요해요.</GateMessage>
      ) : (
        <>
          {/* 상태 탭 — 대기중(기본) + 처리 이력 확인용 */}
          <div className="mb-4 flex gap-1.5">
            {STATUS_TABS.map((t) => (
              <button
                key={t.value}
                type="button"
                onClick={() => selectStatus(t.value)}
                aria-pressed={status === t.value}
                className={
                  "rounded-full px-3 py-1.5 text-[12.5px] font-bold transition-colors " +
                  (status === t.value ? "bg-forest text-cream" : "border border-line-cream bg-white text-ink-2")
                }
              >
                {t.label}
              </button>
            ))}
          </div>

          {isLoading ? (
            <div className="py-10 text-center text-[13px] text-muted">신고를 불러오는 중…</div>
          ) : isError ? (
            <GateMessage>권한이 없거나 세션이 만료됐어요.</GateMessage>
          ) : !data || data.flags.length === 0 ? (
            <div className="py-10 text-center text-[13px] text-muted">
              {status === "PENDING" ? "대기중인 신고가 없어요 👍" : "해당 상태의 신고가 없어요"}
            </div>
          ) : (
            <>
              <ul className="flex flex-col gap-3">
                {data.flags.map((f) => (
                  <FlagCard key={f.id} flag={f} showActions={status === "PENDING"} />
                ))}
              </ul>
              {/* 이전/다음 페이저 — 이력 탭(createdAt ASC)의 20건 초과분 도달용. §9대로 최소 표면(작은 버튼). */}
              {(page > 0 || data.hasNext) && (
                <div className="mt-4 flex items-center justify-center gap-3 text-[13px]">
                  <button
                    type="button"
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                    disabled={page === 0}
                    className="rounded-full px-3 py-1.5 font-bold text-teal disabled:text-muted-3"
                  >
                    이전
                  </button>
                  <span className="text-muted">{page + 1}쪽</span>
                  <button
                    type="button"
                    onClick={() => setPage((p) => p + 1)}
                    disabled={!data.hasNext}
                    className="rounded-full px-3 py-1.5 font-bold text-teal disabled:text-muted-3"
                  >
                    다음
                  </button>
                </div>
              )}
            </>
          )}
        </>
      )}
    </div>
  );
}

function GateMessage({ children }: { children: React.ReactNode }) {
  return (
    <div className="mt-2 rounded-[14px] border border-line-cream bg-white px-4 py-6 text-center text-[13px] text-ink-3">
      {children}
    </div>
  );
}

function FlagCard({ flag, showActions }: { flag: AdminFlagItem; showActions: boolean }) {
  const { show } = useToast();
  const resolve = useResolveFlag();

  const act = (next: "RESOLVED" | "DISMISSED") => {
    if (resolve.isPending) return;
    resolve.mutate(
      { id: flag.id, status: next },
      {
        onSuccess: () => show(next === "RESOLVED" ? "대상을 숨김 처리했어요" : "신고를 반려했어요"),
        onError: () => show("처리에 실패했어요 · 잠시 후 다시 시도해 주세요"),
      },
    );
  };

  return (
    <li className="rounded-[14px] border border-line-cream bg-white p-3.5">
      <div className="flex items-center gap-2">
        <span className="rounded-full bg-mint px-2 py-0.5 text-[11px] font-bold text-teal-deep">
          {TARGET_LABEL[flag.targetType] ?? flag.targetType}
        </span>
        <span className="rounded-full bg-cream px-2 py-0.5 text-[11px] font-semibold text-ink-3">
          {FLAG_REASON_LABEL[flag.reason]}
        </span>
        <span className="ml-auto text-[11px] text-muted">{formatRelativeTime(flag.createdAt)}</span>
      </div>

      {/* 대상 요약 — 관리자가 원본을 별도 조회하지 않고 바로 판단(백엔드가 조립). 삭제됐으면 안내. */}
      <p className="mt-2 text-[13px] text-ink-2">
        {flag.targetExists ? (flag.targetSummary ?? "요약 없음") : "이미 삭제된 대상이에요"}
      </p>
      {flag.detail && <p className="mt-1 text-[12.5px] text-muted">신고 내용: {flag.detail}</p>}
      <p className="mt-1 text-[11px] text-muted-3">
        신고자 #{flag.reporterId} · 대상 #{flag.targetId}
      </p>

      {showActions && (
        <div className="mt-3 flex gap-2.5">
          <button
            type="button"
            onClick={() => act("RESOLVED")}
            disabled={resolve.isPending}
            className="h-[42px] flex-1 rounded-[12px] bg-forest text-[13px] font-bold text-cream disabled:opacity-60"
          >
            대상 숨김
          </button>
          <button
            type="button"
            onClick={() => act("DISMISSED")}
            disabled={resolve.isPending}
            className="h-[42px] flex-1 rounded-[12px] border border-line-cream bg-white text-[13px] font-bold text-muted disabled:opacity-60"
          >
            반려
          </button>
        </div>
      )}
    </li>
  );
}
