"use client";

import Link from "next/link";
import { useState } from "react";
import { Icon } from "@/components/ui/Icon";
import { ApiError } from "@/lib/api";
import { useToast } from "@/lib/context/toast";
import { useCreateReview, useMe, usePlaceReviews } from "@/lib/queries";
import { formatRelativeTime } from "@/lib/reports";

// 별점 표시/입력 공용 — 채움은 Icon의 filled 오버라이드로 별마다 개별 제어(별점 인풋에는 onSelect로 클릭 가능하게).
function Stars({ value, size = 14, onSelect }: { value: number; size?: number; onSelect?: (n: number) => void }) {
  return (
    <span className="flex items-center gap-0.5">
      {[1, 2, 3, 4, 5].map((n) =>
        onSelect ? (
          <button key={n} type="button" onClick={() => onSelect(n)} aria-label={`별점 ${n}점`}>
            <Icon name="star" size={size} filled={n <= value} className={n <= value ? "text-teal" : "text-line-dashed"} />
          </button>
        ) : (
          <Icon key={n} name="star" size={size} filled={n <= value} className={n <= value ? "text-teal" : "text-line-dashed"} />
        ),
      )}
    </span>
  );
}

// 후기 목록 — 공개 조회, 최신순. 제보(RecentReports)와 대칭 패턴.
function ReviewList({ placeId }: { placeId: number }) {
  const { data, isLoading, isError } = usePlaceReviews(placeId);
  if (isLoading) {
    return <p className="py-1 text-[12.5px] text-muted">후기를 불러오는 중…</p>;
  }
  if (isError) {
    return <p className="py-1 text-[12.5px] text-muted">후기를 불러오지 못했어요</p>;
  }
  const reviews = data?.reviews ?? [];
  if (reviews.length === 0) {
    return <p className="py-1 text-[12.5px] text-muted">아직 후기가 없어요 · 첫 후기를 남겨보세요</p>;
  }
  return (
    <ul className="flex flex-col gap-3">
      {reviews.map((r) => (
        <li key={r.id} className="border-b border-line-cream pb-3 last:border-none last:pb-0">
          <div className="flex items-center justify-between gap-2">
            <div className="flex min-w-0 items-center gap-2">
              <span className="truncate text-[13px] font-bold text-ink">{r.authorNickname}</span>
              <Stars value={r.rating} />
            </div>
            <span className="shrink-0 text-[11px] text-muted">{formatRelativeTime(r.createdAt)}</span>
          </div>
          {r.comment && <p className="mt-1 text-[12.5px] text-ink-3">{r.comment}</p>}
        </li>
      ))}
    </ul>
  );
}

// 후기 작성/수정 폼 — 로그인 시에만 렌더(호출부가 useMe로 분기). 장소당 1건 upsert(백엔드 정책)라
// 재제출은 "재작성"으로 동작 — 폼은 항상 새 값으로 초기화(기존 값 프리필은 P2 후속).
function ReviewForm({ placeId }: { placeId: number }) {
  const { show } = useToast();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const mutation = useCreateReview();

  const onSubmit = () => {
    if (rating === 0 || mutation.isPending) return;
    mutation.mutate(
      { placeId, rating, comment: comment.trim() || undefined },
      {
        onSuccess: () => {
          show("후기가 등록됐어요. 고마워요!");
          setComment("");
        },
        onError: (err) => {
          if (err instanceof ApiError && err.status === 401) {
            show("로그인이 필요해요");
          } else {
            show("후기 등록에 실패했어요 · 다시 시도해 주세요");
          }
        },
      },
    );
  };

  return (
    <div className="mb-3 flex flex-col gap-2.5 rounded-[12px] border border-line-cream bg-cream/60 p-3">
      <Stars value={rating} size={22} onSelect={setRating} />
      <textarea
        value={comment}
        onChange={(e) => setComment(e.target.value.slice(0, 1000))}
        placeholder="이 장소는 어땠나요? (선택)"
        aria-label="후기 코멘트"
        rows={2}
        maxLength={1000}
        className="w-full resize-none rounded-[10px] border border-line-cream bg-white px-3 py-2 text-[13px] text-ink placeholder:text-muted-2 focus:border-teal focus:outline-none"
      />
      <button
        type="button"
        onClick={onSubmit}
        disabled={rating === 0 || mutation.isPending}
        className="h-[40px] w-full rounded-[10px] text-[13px] font-bold text-cream disabled:text-ink-3"
        style={{
          background: rating > 0 && !mutation.isPending ? "var(--color-forest)" : "var(--color-btn-disabled)",
        }}
      >
        {mutation.isPending ? "등록하는 중…" : "후기 등록"}
      </button>
    </div>
  );
}

/**
 * 장소 상세 "후기" 섹션 — 영구 평판 콘텐츠(survival_score와 완전 분리, CLAUDE.md §1/§5).
 * 조회(GET /places/{id}/reviews)는 공개, 작성(POST /reviews)은 로그인 필요 — 비로그인은 안내 링크만.
 */
export function ReviewsSection({ placeId }: { placeId: number }) {
  const { data: me, isLoading: meLoading } = useMe();

  return (
    <section className="rounded-[14px] border border-line-cream bg-white p-3.5">
      <div className="mb-2 flex items-center gap-2">
        <h3 className="text-[14px] font-extrabold text-ink-2">후기</h3>
        <span className="rounded-full bg-mint px-2 py-0.5 text-[10px] font-bold text-teal-deep">영구 평판</span>
      </div>

      {meLoading ? null : me ? (
        <ReviewForm placeId={placeId} />
      ) : (
        <Link
          href="/mypage"
          className="mb-3 flex items-center justify-between rounded-[12px] border border-line-cream bg-cream/60 px-3.5 py-3 text-[13px] font-semibold text-teal"
        >
          로그인하고 후기 남기기
          <Icon name="chev" size={16} />
        </Link>
      )}

      <ReviewList placeId={placeId} />
    </section>
  );
}
