import { PlaceDetailOverlay } from "@/components/place/PlaceDetailOverlay";
import { TabBar } from "@/components/shell/TabBar";
import { ToastHost } from "@/components/ui/ToastHost";
import { UserProfileOverlay } from "@/components/user/UserProfileOverlay";

// 공통 셸: 모바일 뷰포트(≤430px) 컬럼 + 하단 3탭. 데스크톱에서는 폰 크기 컬럼을 중앙 정렬.
// 장소 상세는 콘텐츠(main) 위 오버레이로 덮되 탭바는 유지한다.
export default function ShellLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh justify-center bg-[#e6e3da]">
      <div className="relative flex h-dvh w-full max-w-[430px] flex-col overflow-hidden bg-cream shadow-[0_0_40px_rgba(0,0,0,0.08)]">
        <main className="relative flex-1 overflow-hidden">
          {children}
          <PlaceDetailOverlay />
          <UserProfileOverlay />
          <ToastHost />
        </main>
        <TabBar />
      </div>
    </div>
  );
}
