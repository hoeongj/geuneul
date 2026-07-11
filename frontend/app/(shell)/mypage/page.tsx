"use client";

import { useEffect, useState } from "react";
import { FollowingSection } from "@/components/mypage/FollowingSection";
import { MyActivitySection } from "@/components/mypage/MyActivitySection";
import { NotificationsSection } from "@/components/notification/NotificationsSection";
import { Icon } from "@/components/ui/Icon";
import { useSelectedPlace } from "@/lib/context/selected";
import { useLogout, useMe, useMyBookmarks } from "@/lib/queries";
import type { User } from "@/types/user";

const ERROR_MESSAGES: Record<string, string> = {
  state: "로그인 요청이 만료됐어요. 다시 시도해 주세요.",
  login: "로그인에 실패했어요. 다시 시도해 주세요.",
  upstream: "서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.",
  config: "로그인 설정이 준비 중이에요.",
  provider: "지원하지 않는 로그인 방식이에요.",
};

export default function MyPage() {
  const { data: me, isLoading } = useMe();
  const [error, setError] = useState<string | null>(null);

  // 콜백 실패 시 /mypage?error=... 로 되돌아온다. useSearchParams의 Suspense 요구를 피해 마운트 후
  // 클라에서 1회 읽는다(SSR은 null → 하이드레이션 일치, 이후 이펙트로 갱신 → 미스매치 없음).
  useEffect(() => {
    const code = new URLSearchParams(window.location.search).get("error");
    if (code) {
      // 마운트 시 URL 쿼리를 플래시 메시지로 옮기는 정당한 1회 동기화 — 캐스케이드 아님.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setError(ERROR_MESSAGES[code] ?? "로그인 중 문제가 발생했어요.");
      window.history.replaceState(null, "", "/mypage"); // 쿼리 제거(새로고침 시 재노출 방지)
    }
  }, []);

  return (
    <div className="relative h-full overflow-y-auto px-4 pt-5 pb-6 lg:mx-auto lg:max-w-[720px] lg:px-8 lg:pt-8">
      <header className="mb-4">
        <h1 className="text-[23px] font-extrabold tracking-[-0.4px] text-ink">내 정보</h1>
        <p className="mt-1 text-[13px] text-ink-3">로그인하면 후기 작성과 신뢰도 배지를 쓸 수 있어요.</p>
      </header>

      {error && (
        <div
          role="alert"
          className="mb-4 rounded-[12px] border border-[#e6c9c0] bg-[#fbeee9] px-3.5 py-3 text-[13px] text-[#9a3b28]"
        >
          {error}
        </div>
      )}

      {isLoading ? (
        <div className="py-10 text-center text-[13px] text-muted">불러오는 중…</div>
      ) : me ? (
        <Profile user={me} />
      ) : (
        <LoginOptions />
      )}
    </div>
  );
}

function LoginOptions() {
  return (
    <div className="flex flex-col gap-3">
      <div className="mb-1 rounded-[14px] border border-line-cream bg-white px-4 py-4 text-[13px] leading-relaxed text-ink-3">
        그늘은 로그인 없이도 지도·제보를 쓸 수 있어요. 로그인하면{" "}
        <span className="font-semibold text-ink">후기 작성</span>과{" "}
        <span className="font-semibold text-ink">신뢰도 반영</span>이 추가됩니다.
      </div>

      {/* OAuth 시작은 브라우저 전체 이동이어야 한다(제공자로 302). next/link는 client-nav·prefetch라
          인가 라우트를 잘못 트리거하므로 의도적으로 <a>를 쓴다. */}
      {/* eslint-disable-next-line @next/next/no-html-link-for-pages */}
      <a
        href="/api/auth/kakao"
        className="flex h-[52px] w-full items-center justify-center gap-2 rounded-[14px] text-[15px] font-bold"
        style={{ background: "#FEE500", color: "#191600" }}
      >
        카카오로 로그인
      </a>
      {/* eslint-disable-next-line @next/next/no-html-link-for-pages */}
      <a
        href="/api/auth/google"
        className="flex h-[52px] w-full items-center justify-center gap-2 rounded-[14px] border border-line-cream bg-white text-[15px] font-bold text-ink"
      >
        Google로 로그인
      </a>
    </div>
  );
}

function Profile({ user }: { user: User }) {
  const logout = useLogout();
  const providerLabel = user.provider === "KAKAO" ? "카카오" : "Google";

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-3.5 rounded-[16px] border border-line-cream bg-white px-4 py-4">
        <Avatar user={user} />
        <div className="min-w-0 flex-1">
          <div className="truncate text-[17px] font-extrabold text-ink">{user.nickname}</div>
          <div className="mt-0.5 flex items-center gap-2 text-[12px] text-muted">
            <span className="rounded-full bg-cream px-2 py-0.5 font-semibold text-ink-3">{providerLabel}</span>
            {user.email && <span className="truncate">{user.email}</span>}
          </div>
        </div>
      </div>

      <div className="flex items-center justify-between rounded-[14px] border border-line-cream bg-white px-4 py-3.5">
        <span className="text-[13px] text-ink-3">신뢰도</span>
        <span className="text-[15px] font-bold text-teal">{Math.round(user.trustScore)}</span>
      </div>

      <NotificationsSection />

      <MyActivitySection />

      <FollowingSection />

      <BookmarksSection />

      <button
        type="button"
        onClick={() => logout.mutate()}
        disabled={logout.isPending}
        className="h-[48px] w-full rounded-[14px] border border-line-cream bg-white text-[14px] font-bold text-muted disabled:opacity-60"
      >
        로그아웃
      </button>
    </div>
  );
}

// 관심 장소(A7) — Profile 안에서만 렌더(로그인 상태). 항목 클릭 시 상세 오버레이를 연다(shell 레이아웃 공용).
function BookmarksSection() {
  const selected = useSelectedPlace();
  const { data, isLoading } = useMyBookmarks(true);
  const bookmarks = data ?? [];

  return (
    <div className="overflow-hidden rounded-[14px] border border-line-cream bg-white">
      <div className="flex items-center gap-2 border-b border-line-cream px-4 py-3">
        <h2 className="text-[14px] font-extrabold text-ink-2">관심 장소</h2>
        {!isLoading && <span className="text-[12px] text-muted">{bookmarks.length}</span>}
      </div>
      {isLoading ? (
        <p className="px-4 py-4 text-[13px] text-muted">불러오는 중…</p>
      ) : bookmarks.length === 0 ? (
        <p className="px-4 py-4 text-[13px] text-muted">저장한 장소가 없어요 · 장소 상세에서 ★로 저장하세요</p>
      ) : (
        <ul>
          {bookmarks.map((b) => (
            <li key={b.placeId}>
              <button
                type="button"
                onClick={() => selected.open(b.placeId)}
                className="flex w-full items-center justify-between gap-2 border-b border-line-cream px-4 py-3 text-left last:border-none"
              >
                <span className="min-w-0">
                  <span className="block truncate text-[14px] font-bold text-ink">{b.name}</span>
                  <span className="block truncate text-[12px] text-ink-3">
                    {b.categoryLabel}
                    {b.memo ? ` · ${b.memo}` : ""}
                  </span>
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

function Avatar({ user }: { user: User }) {
  if (user.profileImage) {
    return (
      // 외부(카카오/구글) 이미지 — next/image 도메인 설정 없이 단순 img. no-referrer로 핫링크 차단 회피.
      // eslint-disable-next-line @next/next/no-img-element
      <img
        src={user.profileImage}
        alt=""
        width={52}
        height={52}
        referrerPolicy="no-referrer"
        className="h-[52px] w-[52px] shrink-0 rounded-full object-cover"
      />
    );
  }
  return (
    <span className="flex h-[52px] w-[52px] shrink-0 items-center justify-center rounded-full bg-cream text-forest">
      <Icon name="user" size={26} />
    </span>
  );
}
