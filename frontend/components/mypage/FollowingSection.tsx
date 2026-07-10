"use client";

import { Icon } from "@/components/ui/Icon";
import { useSelectedUser } from "@/lib/context/selectedUser";
import { useMyFollowing } from "@/lib/queries";

// 내 팔로잉(N7) — "나만" 보는 목록(§0-9). 팔로우한 작성자를 다시 찾아가는 사적 북마크.
// (팔로워 "목록"은 어디에도 없다 — 프로필엔 카운트만.)
export function FollowingSection() {
  const selectedUser = useSelectedUser();
  const { data, isLoading } = useMyFollowing(true);
  const following = data ?? [];

  return (
    <div className="overflow-hidden rounded-[14px] border border-line-cream bg-white">
      <div className="flex items-center gap-2 border-b border-line-cream px-4 py-3">
        <h2 className="text-[14px] font-extrabold text-ink-2">팔로잉</h2>
        {!isLoading && <span className="text-[12px] text-muted">{following.length}</span>}
      </div>
      {isLoading ? (
        <p className="px-4 py-4 text-[13px] text-muted">불러오는 중…</p>
      ) : following.length === 0 ? (
        <p className="px-4 py-4 text-[13px] text-muted">팔로우한 작성자가 없어요 · 후기 닉네임을 눌러 프로필에서 팔로우하세요</p>
      ) : (
        <ul>
          {following.map((f) => (
            <li key={f.userId}>
              <button
                type="button"
                onClick={() => selectedUser.open(f.userId)}
                className="flex w-full items-center gap-3 border-b border-line-cream px-4 py-3 text-left last:border-none active:bg-cream"
              >
                {f.profileImage ? (
                  // eslint-disable-next-line @next/next/no-img-element -- 외부 프로필 이미지
                  <img
                    src={f.profileImage}
                    alt=""
                    referrerPolicy="no-referrer"
                    className="h-9 w-9 shrink-0 rounded-full object-cover"
                  />
                ) : (
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-cream text-forest">
                    <Icon name="user" size={18} />
                  </span>
                )}
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[14px] font-bold text-ink">{f.nickname}</span>
                  <span className="block text-[11.5px] text-muted">신뢰도 {Math.round(f.trustScore)}</span>
                </span>
                <Icon name="chev" size={16} className="shrink-0 text-muted-3" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
