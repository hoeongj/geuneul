import { PlaceDetailOverlay } from "@/components/place/PlaceDetailOverlay";
import { NavRail } from "@/components/shell/NavRail";
import { TabBar } from "@/components/shell/TabBar";
import { ToastHost } from "@/components/ui/ToastHost";
import { UserProfileOverlay } from "@/components/user/UserProfileOverlay";

// 공통 셸.
// - 모바일(<lg): 폰 뷰포트(≤430px) 컬럼 + 하단 3탭(기존 그대로).
// - 데스크톱(≥lg): 좌측 내비 레일 + 폭 제한 없는 콘텐츠 영역. 지도 페이지는 콘텐츠 안에서
//   좌측 사이드바 + 풀블리드 지도로 스스로 분기한다(카카오/네이버맵 데스크톱 표준).
// 장소 상세/작성자는 콘텐츠(main) 위 오버레이 — 데스크톱에선 좌측 패널로 좁혀 지도를 가리지 않는다.
export default function ShellLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh justify-center bg-[#e6e3da] lg:justify-start lg:bg-cream">
      <NavRail className="hidden lg:flex" />
      <div className="relative flex h-dvh w-full max-w-[430px] flex-col overflow-hidden bg-cream shadow-[0_0_40px_rgba(0,0,0,0.08)] lg:max-w-none lg:flex-1 lg:shadow-none">
        <main className="relative flex-1 overflow-hidden">
          {children}
          <PlaceDetailOverlay />
          <UserProfileOverlay />
          <ToastHost />
        </main>
        <TabBar className="lg:hidden" />
      </div>
    </div>
  );
}
