import { Icon } from "@/components/ui/Icon";

export const metadata = { title: "오프라인" };

// 오프라인 셸: 네트워크가 끊겼을 때 document 폴백.
export default function OfflinePage() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3 bg-cream px-8 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-mint text-forest">
        <Icon name="mapicon" size={30} />
      </div>
      <h1 className="text-[18px] font-extrabold text-ink">오프라인이에요</h1>
      <p className="text-[13px] text-ink-3">
        인터넷 연결이 끊겼어요. 다시 연결되면 주변 그늘을 이어서 찾아드릴게요.
      </p>
    </div>
  );
}
