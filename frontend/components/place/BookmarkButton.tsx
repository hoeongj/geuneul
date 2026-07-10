"use client";

import { Icon } from "@/components/ui/Icon";
import { useToast } from "@/lib/context/toast";
import { useMe, useMyBookmarks, useToggleBookmark } from "@/lib/queries";

// 관심 장소 저장 토글(A7) — 상세 헤더. 리액션과 달리 백엔드에 목록 GET이 있어 초기 상태를 안다(useMyBookmarks
// 멤버십). 비로그인은 클릭 시 안내만(백엔드 왕복 없음, 후기/댓글과 동일 UX). survival_score(간판)와 무관한 살.
export function BookmarkButton({ placeId }: { placeId: number }) {
  const { show } = useToast();
  const { data: me } = useMe();
  const { data: bookmarks } = useMyBookmarks(!!me);
  const toggle = useToggleBookmark();
  const saved = !!bookmarks?.some((b) => b.placeId === placeId);

  const onClick = () => {
    if (!me) {
      show("로그인이 필요해요");
      return;
    }
    if (toggle.isPending) return;
    toggle.mutate(
      { placeId, next: !saved },
      {
        onSuccess: () => show(saved ? "관심 장소에서 뺐어요" : "관심 장소에 저장했어요"),
        onError: () => show("잠시 후 다시 시도해 주세요"),
      },
    );
  };

  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={saved}
      aria-label={saved ? "관심 장소 해제" : "관심 장소 저장"}
      className="flex h-[38px] w-[38px] items-center justify-center"
    >
      <Icon name="star" size={20} filled={saved} className={saved ? "text-teal" : "text-ink"} />
    </button>
  );
}
