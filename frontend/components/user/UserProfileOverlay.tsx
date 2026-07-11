"use client";

import { Icon } from "@/components/ui/Icon";
import { useSelectedPlace } from "@/lib/context/selected";
import { useSelectedUser } from "@/lib/context/selectedUser";
import { useToast } from "@/lib/context/toast";
import { useMe, useToggleFollow, useUserProfile } from "@/lib/queries";
import { formatRelativeTime } from "@/lib/reports";
import type { UserProfile } from "@/types/follow";

// 작성자 공개 프로필(N7) — 후기 닉네임 탭으로 진입. 닉네임·신뢰도·팔로워"수"·공개 후기.
// 커먼스라 누구나 본다. 팔로워 "목록"은 어디에도 없다(§0-9) — 카운트만.
export function UserProfileOverlay() {
  const { id, close } = useSelectedUser();
  const { data: profile, isLoading, isError } = useUserProfile(id);
  const { data: me } = useMe();

  if (id == null) return null;

  return (
    <div className="gn-overlay absolute inset-0 z-50 flex flex-col overflow-y-auto bg-cream lg:right-auto lg:w-[400px] lg:border-r lg:border-line-cream lg:shadow-[0_0_40px_rgba(0,0,0,0.14)]">
      <header className="sticky top-0 z-10 flex items-center justify-between border-b border-line-cream bg-cream px-2 py-2">
        <button type="button" onClick={close} aria-label="뒤로" className="flex h-[38px] w-[38px] items-center justify-center text-ink">
          <Icon name="chevLeft" size={22} />
        </button>
        <h1 className="text-[15px] font-extrabold text-ink">작성자</h1>
        <span className="w-[38px]" />
      </header>

      {isLoading ? (
        <p className="py-10 text-center text-[13px] text-muted">불러오는 중…</p>
      ) : isError || !profile ? (
        <p className="py-10 text-center text-[13px] text-muted">프로필을 불러오지 못했어요</p>
      ) : (
        <ProfileBody profile={profile} isSelf={me?.id === profile.id} loggedIn={!!me} onClose={close} />
      )}
    </div>
  );
}

function ProfileBody({
  profile,
  isSelf,
  loggedIn,
  onClose,
}: {
  profile: UserProfile;
  isSelf: boolean;
  loggedIn: boolean;
  onClose: () => void;
}) {
  const selectedPlace = useSelectedPlace();
  const openPlace = (placeId: number) => {
    selectedPlace.open(placeId);
    onClose(); // 프로필을 닫고 장소 상세로 전환
  };

  return (
    <div className="flex flex-col gap-4 px-4 py-4">
      {/* 프로필 헤더 */}
      <div className="flex items-center gap-3.5 rounded-[16px] border border-line-cream bg-white px-4 py-4">
        {profile.profileImage ? (
          // eslint-disable-next-line @next/next/no-img-element -- 외부(카카오/구글) 프로필 이미지
          <img
            src={profile.profileImage}
            alt=""
            width={52}
            height={52}
            referrerPolicy="no-referrer"
            className="h-[52px] w-[52px] shrink-0 rounded-full object-cover"
          />
        ) : (
          <span className="flex h-[52px] w-[52px] shrink-0 items-center justify-center rounded-full bg-cream text-forest">
            <Icon name="user" size={26} />
          </span>
        )}
        <div className="min-w-0 flex-1">
          <div className="truncate text-[17px] font-extrabold text-ink">{profile.nickname}</div>
          <div className="mt-1 flex items-center gap-2 text-[12px]">
            <span className="rounded-full bg-mint-2 px-2 py-0.5 font-bold text-teal-deep">
              신뢰도 {Math.round(profile.trustScore)}
            </span>
            <span className="text-muted">팔로워 {profile.followerCount}</span>
          </div>
        </div>
      </div>

      {/* 팔로우 버튼 — 본인 프로필엔 없음 */}
      {!isSelf && <FollowButton userId={profile.id} following={profile.following} loggedIn={loggedIn} />}

      {/* 공개 후기 */}
      <div>
        <h3 className="mb-2 text-[14px] font-extrabold text-ink-2">작성한 후기 {profile.reviews.length}</h3>
        {profile.reviews.length === 0 ? (
          <p className="py-2 text-[13px] text-muted">아직 공개 후기가 없어요</p>
        ) : (
          <ul className="flex flex-col gap-2.5">
            {profile.reviews.map((r) => (
              <li key={r.reviewId}>
                <button
                  type="button"
                  onClick={() => openPlace(r.placeId)}
                  className="flex w-full flex-col items-start gap-0.5 rounded-[12px] border border-line-cream bg-white px-3.5 py-3 text-left active:bg-cream"
                >
                  <span className="flex w-full items-center justify-between gap-2">
                    <span className="truncate text-[14px] font-bold text-ink">{r.placeName}</span>
                    <span className="shrink-0 text-[12px] text-teal">{"★".repeat(r.rating)}</span>
                  </span>
                  {r.comment && <span className="line-clamp-2 text-[12.5px] text-ink-3">{r.comment}</span>}
                  <span className="text-[10.5px] text-muted-3">{formatRelativeTime(r.createdAt)}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function FollowButton({ userId, following, loggedIn }: { userId: number; following: boolean; loggedIn: boolean }) {
  const { show } = useToast();
  const toggle = useToggleFollow(userId);

  const onClick = () => {
    if (!loggedIn) {
      show("로그인이 필요해요");
      return;
    }
    if (toggle.isPending) return;
    toggle.mutate(!following, {
      onError: () => show("잠시 후 다시 시도해 주세요"),
    });
  };

  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={following}
      disabled={toggle.isPending}
      className={
        "h-[46px] w-full rounded-[14px] text-[14px] font-bold disabled:opacity-60 " +
        (following ? "border border-line-cream bg-white text-ink-2" : "bg-forest text-cream")
      }
    >
      {following ? "팔로잉 중 · 탭하면 해제" : "팔로우"}
    </button>
  );
}
