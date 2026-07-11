import type { Metadata } from "next";
import { InstallView } from "@/components/install/InstallView";

export const metadata: Metadata = {
  title: "앱 설치",
  description: "그늘을 홈 화면에 설치하세요 — 스토어 없이 무료, 전체화면·오프라인 지원.",
};

// /install — 스토어 없이 $0 설치(D2). 안드로이드=WebAPK 원탭 설치, iOS=홈 화면에 추가 안내.
// (shell) 밖 독립 라우트라 지도 셸/하단 탭이 없다(모바일 셸 무변경).
export default function InstallPage() {
  return <InstallView />;
}
