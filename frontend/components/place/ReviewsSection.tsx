"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { Icon } from "@/components/ui/Icon";
import { ApiError, toggleReaction } from "@/lib/api";
import { useToast } from "@/lib/context/toast";
import { usePhotoUpload } from "@/lib/hooks";
import {
  useCreateReview,
  useCreateReviewComment,
  useMe,
  usePlaceReviews,
  useReviewComments,
} from "@/lib/queries";
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

// "유용해요" 리액션 토글(2차·살, §0-9 — 최소 표면). 초기 상태는 백엔드가 후기에 안 실어 주므로
// 상호작용 기반이다: 클릭 시 POST(추가)/DELETE(취소)의 응답 {reacted,count}로 표시를 갱신한다.
// 비로그인은 클릭 시 안내만(백엔드까지 안 감). 커뮤니티는 간판이 아니라 살이라 카운트를 과시하지 않는다.
function HelpfulToggle({ reviewId, loggedIn }: { reviewId: number; loggedIn: boolean }) {
  const { show } = useToast();
  const [state, setState] = useState<{ reacted: boolean; count: number } | null>(null);
  const [pending, setPending] = useState(false);

  const onClick = async () => {
    if (!loggedIn) {
      show("로그인이 필요해요");
      return;
    }
    if (pending) return;
    const add = !(state?.reacted ?? false);
    setPending(true);
    try {
      const next = await toggleReaction({ targetType: "REVIEW", targetId: reviewId, add });
      setState(next);
    } catch (err) {
      show(err instanceof ApiError && err.status === 401 ? "로그인이 필요해요" : "잠시 후 다시 시도해 주세요");
    } finally {
      setPending(false);
    }
  };

  const reacted = state?.reacted ?? false;
  const count = state?.count ?? 0;
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={reacted}
      className={
        "flex items-center gap-1 rounded-full px-2.5 py-1 text-[12px] font-bold " +
        (reacted ? "bg-mint-2 text-teal-deep" : "bg-cream text-muted")
      }
    >
      <span aria-hidden>👍</span>
      유용해요{count > 0 ? ` ${count}` : ""}
    </button>
  );
}

// 후기 댓글 — 펼쳤을 때만 지연 로드. 작성은 로그인 필요. 커뮤니티가 전면에 나오지 않게 기본 접힘(§0-9).
function ReviewComments({ reviewId, loggedIn }: { reviewId: number; loggedIn: boolean }) {
  const { show } = useToast();
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const { data, isLoading } = useReviewComments(reviewId, open);
  const mutation = useCreateReviewComment(reviewId);

  const submit = () => {
    const comment = text.trim();
    if (!comment || mutation.isPending) return;
    mutation.mutate(comment, {
      onSuccess: () => setText(""),
      onError: (err) =>
        show(err instanceof ApiError && err.status === 401 ? "로그인이 필요해요" : "댓글 등록에 실패했어요"),
    });
  };

  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        className="flex items-center gap-1 rounded-full bg-cream px-2.5 py-1 text-[12px] font-bold text-muted"
      >
        💬 댓글{data && data.length > 0 ? ` ${data.length}` : ""}
      </button>

      {open && (
        <div className="mt-2 flex flex-col gap-2">
          {isLoading ? (
            <p className="text-[12px] text-muted">댓글을 불러오는 중…</p>
          ) : !data || data.length === 0 ? (
            <p className="text-[12px] text-muted">아직 댓글이 없어요</p>
          ) : (
            <ul className="flex flex-col gap-1.5">
              {data.map((c) => (
                <li key={c.id} className="text-[12.5px] text-ink-3">
                  <span className="font-bold text-ink">{c.nickname}</span>{" "}
                  <span>{c.comment}</span>{" "}
                  <span className="text-[10.5px] text-muted-3">{formatRelativeTime(c.createdAt)}</span>
                </li>
              ))}
            </ul>
          )}

          {loggedIn ? (
            <div className="flex items-center gap-1.5">
              <input
                value={text}
                onChange={(e) => setText(e.target.value.slice(0, 300))}
                onKeyDown={(e) => {
                  if (e.key === "Enter") submit();
                }}
                placeholder="댓글 달기"
                aria-label="댓글 입력"
                maxLength={300}
                className="min-w-0 flex-1 rounded-[8px] border border-line-cream bg-white px-2.5 py-1.5 text-[12.5px] text-ink placeholder:text-muted-2 focus:border-teal focus:outline-none"
              />
              <button
                type="button"
                onClick={submit}
                disabled={!text.trim() || mutation.isPending}
                className="shrink-0 rounded-[8px] bg-forest px-3 py-1.5 text-[12px] font-bold text-cream disabled:opacity-40"
              >
                등록
              </button>
            </div>
          ) : (
            <p className="text-[11.5px] text-muted-2">로그인하면 댓글을 남길 수 있어요</p>
          )}
        </div>
      )}
    </div>
  );
}

// 후기 목록 — 공개 조회, 최신순. 제보(RecentReports)와 대칭 패턴.
function ReviewList({ placeId, loggedIn }: { placeId: number; loggedIn: boolean }) {
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
          {/* 2차 커뮤니티(살) — 유용해요 + 댓글. 최소 표면(§0-9). */}
          <div className="mt-2 flex items-start gap-2">
            <HelpfulToggle reviewId={r.id} loggedIn={loggedIn} />
            <div className="min-w-0 flex-1">
              <ReviewComments reviewId={r.id} loggedIn={loggedIn} />
            </div>
          </div>
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
  const photo = usePhotoUpload("review");
  const fileInputRef = useRef<HTMLInputElement>(null);

  const onSubmit = () => {
    if (rating === 0 || mutation.isPending || photo.state === "uploading") return;
    mutation.mutate(
      { placeId, rating, comment: comment.trim() || undefined, photos: photo.objectUrl ? [photo.objectUrl] : undefined },
      {
        onSuccess: () => {
          show("후기가 등록됐어요. 고마워요!");
          setComment("");
          photo.reset();
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
      <div className="flex items-center gap-2.5">
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          hidden
          onChange={(e) => {
            const file = e.target.files?.[0];
            e.target.value = "";
            if (file) photo.pick(file);
          }}
        />
        <button
          type="button"
          onClick={() => (photo.state === "done" ? photo.reset() : fileInputRef.current?.click())}
          className="relative flex h-[44px] w-[44px] shrink-0 items-center justify-center overflow-hidden rounded-[10px] border border-dashed border-line-dashed bg-white text-muted"
          aria-label={photo.state === "done" ? "사진 제거" : "사진 추가"}
        >
          {photo.previewUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- presigned S3 오브젝트(blob 미리보기 포함)
            <img src={photo.previewUrl} alt="" className="h-full w-full object-cover" />
          ) : (
            <Icon name="camera" size={16} />
          )}
          {photo.state === "uploading" && <span className="absolute inset-0 bg-black/40" />}
        </button>
        {photo.state === "error" && photo.errorMessage && (
          <span className="text-[11.5px] text-red-500">{photo.errorMessage}</span>
        )}
      </div>
      <button
        type="button"
        onClick={onSubmit}
        disabled={rating === 0 || mutation.isPending || photo.state === "uploading"}
        className="h-[40px] w-full rounded-[10px] text-[13px] font-bold text-cream disabled:text-ink-3"
        style={{
          background: rating > 0 && !mutation.isPending ? "var(--color-forest)" : "var(--color-btn-disabled)",
        }}
      >
        {mutation.isPending ? "등록하는 중…" : photo.state === "uploading" ? "사진 올리는 중…" : "후기 등록"}
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

      <ReviewList placeId={placeId} loggedIn={!!me} />
    </section>
  );
}
