"use client";

import { useState } from "react";
import { Icon } from "@/components/ui/Icon";
import { useSelectedPlace } from "@/lib/context/selected";
import { useMyComments, useMyReactions, useMyReviews } from "@/lib/queries";
import { formatRelativeTime } from "@/lib/reports";

// 내 글 관리(N6) — 내가 쓴 후기/댓글/유용해요를 탭으로 모아본다. 각 항목 탭 시 원문 장소 상세를 연다.
// 커뮤니티 "살"의 개인 관리 뷰일 뿐 공개 피드가 아니다(§0-9).
type Tab = "reviews" | "comments" | "reactions";

interface Row {
  key: string;
  placeId: number;
  placeName: string;
  subtitle: string | null;
  createdAt: string;
}

export function MyActivitySection() {
  const [tab, setTab] = useState<Tab>("reviews");
  const selected = useSelectedPlace();

  const reviews = useMyReviews(true);
  const comments = useMyComments(true);
  const reactions = useMyReactions(true);

  const counts = {
    reviews: reviews.data?.length ?? 0,
    comments: comments.data?.length ?? 0,
    reactions: reactions.data?.length ?? 0,
  };

  const tabs: { key: Tab; label: string }[] = [
    { key: "reviews", label: "후기" },
    { key: "comments", label: "댓글" },
    { key: "reactions", label: "유용해요" },
  ];

  const active = tab === "reviews" ? reviews : tab === "comments" ? comments : reactions;

  const rows: Row[] =
    tab === "reviews"
      ? (reviews.data ?? []).map((r) => ({
          key: `rv-${r.reviewId}`,
          placeId: r.placeId,
          placeName: r.placeName,
          subtitle: `${"★".repeat(r.rating)}${r.comment ? ` · ${r.comment}` : ""}`,
          createdAt: r.createdAt,
        }))
      : tab === "comments"
        ? (comments.data ?? []).map((c) => ({
            key: `cm-${c.commentId}`,
            placeId: c.placeId,
            placeName: c.placeName,
            subtitle: c.comment,
            createdAt: c.createdAt,
          }))
        : (reactions.data ?? []).map((rx) => ({
            key: `rx-${rx.reactionId}`,
            placeId: rx.placeId,
            placeName: rx.placeName,
            subtitle: rx.reviewComment,
            createdAt: rx.createdAt,
          }));

  const emptyText =
    tab === "reviews"
      ? "아직 쓴 후기가 없어요 · 장소 상세에서 남겨보세요"
      : tab === "comments"
        ? "아직 쓴 댓글이 없어요"
        : "아직 유용해요를 누른 글이 없어요";

  return (
    <div className="overflow-hidden rounded-[14px] border border-line-cream bg-white">
      <div className="border-b border-line-cream px-4 py-3">
        <h2 className="text-[14px] font-extrabold text-ink-2">내 활동</h2>
      </div>

      <div className="flex gap-1 px-2 pt-2">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            aria-pressed={tab === t.key}
            className={
              "flex-1 rounded-[10px] py-2 text-[12.5px] font-bold " +
              (tab === t.key ? "bg-mint text-teal-deep" : "text-muted")
            }
          >
            {t.label}
            {counts[t.key] > 0 ? ` ${counts[t.key]}` : ""}
          </button>
        ))}
      </div>

      <div className="px-2 pb-2">
        {active.isLoading ? (
          <p className="px-2 py-4 text-[13px] text-muted">불러오는 중…</p>
        ) : rows.length === 0 ? (
          <p className="px-2 py-4 text-[13px] text-muted">{emptyText}</p>
        ) : (
          <ul>
            {rows.map((row) => (
              <li key={row.key}>
                <button
                  type="button"
                  onClick={() => selected.open(row.placeId)}
                  className="flex w-full items-center justify-between gap-2 rounded-[10px] px-2 py-2.5 text-left active:bg-cream"
                >
                  <span className="min-w-0">
                    <span className="block truncate text-[13.5px] font-bold text-ink">{row.placeName}</span>
                    {row.subtitle && (
                      <span className="block truncate text-[12px] text-ink-3">{row.subtitle}</span>
                    )}
                    <span className="block text-[10.5px] text-muted-3">{formatRelativeTime(row.createdAt)}</span>
                  </span>
                  <Icon name="chev" size={16} className="shrink-0 text-muted-3" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
