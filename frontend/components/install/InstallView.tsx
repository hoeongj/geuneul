"use client";

import Link from "next/link";
import { useState, useSyncExternalStore } from "react";
import { Icon } from "@/components/ui/Icon";
import type { IconName } from "@/lib/icon-paths";
import { useInstallState } from "@/lib/hooks";
import { detectPlatform, promptInstall, type Platform } from "@/lib/pwa-install";

// 설치 랜딩(D2). 플랫폼 감지 → 안드로이드는 원탭 WebAPK 설치, iOS는 "공유 → 홈 화면에 추가" 안내,
// 데스크톱은 설치 가능 시 버튼 + 휴대폰 유도. (shell) 밖이라 ToastHost가 없어 피드백은 인라인.
export function InstallView() {
  const { canInstall, installed } = useInstallState();
  const [status, setStatus] = useState<string | null>(null);

  // 플랫폼 감지는 클라에서만(navigator) → 서버 스냅샷 null로 하이드레이션 안전(effect setState 회피).
  const platform = useSyncExternalStore<Platform | null>(
    () => () => {},
    () => detectPlatform(),
    () => null,
  );

  const onInstall = async () => {
    setStatus(null);
    const outcome = await promptInstall();
    if (outcome === "accepted") setStatus("설치를 시작했어요. 홈 화면에서 그늘을 열어보세요.");
    else if (outcome === "dismissed") setStatus("설치를 취소했어요. 언제든 다시 눌러 설치할 수 있어요.");
    else setStatus("아직 설치 버튼을 띄울 수 없어요. 브라우저 메뉴에서 '앱 설치'를 눌러도 돼요.");
  };

  return (
    <div className="min-h-dvh bg-cream px-6 py-10">
      <div className="mx-auto flex w-full max-w-[440px] flex-col items-center">
        <Link
          href="/"
          className="mb-8 self-start text-[13px] font-semibold text-ink-3 transition-colors hover:text-teal"
        >
          ← 지도로 돌아가기
        </Link>

        {/* 히어로 */}
        <div className="w-[140px]">
          {/* eslint-disable-next-line @next/next/no-img-element -- 로컬 브랜드 에셋 */}
          <img src="/brand/character.png" alt="그늘" className="w-full select-none" draggable={false} />
        </div>
        <h1 className="mt-3 text-[26px] font-extrabold tracking-[-0.5px] text-teal">그늘 설치</h1>
        <p className="mt-2 text-center text-[13.5px] leading-relaxed text-ink-3">
          홈 화면에 추가하면 <b className="text-ink-2">전체화면·오프라인</b>으로 더 빠르게 열려요.
          <br />
          <b className="text-ink-2">스토어 없이 무료</b> — 설치 파일도, 계정도 필요 없어요.
        </p>

        {/* 이미 설치됨 */}
        {installed ? (
          <div className="mt-8 flex w-full items-center gap-3 rounded-2xl border border-line-cream bg-mint-3 px-5 py-4 text-[14px] font-semibold text-teal-deep">
            <CheckIcon />
            이미 설치되어 있어요. 홈 화면 아이콘으로 열어보세요.
          </div>
        ) : (
          <PrimaryAction
            platform={platform}
            canInstall={canInstall}
            onInstall={onInstall}
          />
        )}

        {status && (
          <p className="mt-4 w-full rounded-xl bg-mint-2 px-4 py-3 text-center text-[12.5px] font-semibold text-ink-2">
            {status}
          </p>
        )}

        {/* APK 직접 다운로드(안드로이드) — WebAPK 원탭이 안 될 때/파일로 받고 싶을 때. iOS는 불가라 숨김. */}
        {platform !== "ios" && !installed && (
          <div className="mt-4 w-full">
            <a
              href="/geuneul.apk"
              download="geuneul.apk"
              className="flex w-full items-center justify-center gap-2 rounded-2xl border border-line-cream bg-white px-5 py-3.5 text-[13px] font-bold text-ink-2 transition-colors hover:border-teal/50"
            >
              <DownloadIcon />
              APK 파일 직접 다운로드 <span className="font-medium text-ink-3">(안드로이드)</span>
            </a>
            <p className="mt-2 text-center text-[11px] leading-relaxed text-muted">
              서명된 TWA 빌드 · 설치 시 “출처를 알 수 없는 앱” 허용이 필요해요. 간편 설치는 위의 <b className="text-ink-3">앱 설치</b>(WebAPK)를 권장.
            </p>
          </div>
        )}

        {/* 왜 설치? */}
        <ul className="mt-10 grid w-full grid-cols-2 gap-3">
          <Feature icon="mapicon" title="전체화면 지도" desc="주소창 없이 넓게" />
          <Feature icon="bolt" title="빠른 실행" desc="홈 아이콘 원탭" />
          <Feature icon="snow" title="오프라인 셸" desc="끊겨도 기본 동작" />
          <Feature icon="share" title="공유 링크" desc="장소를 그대로 전달" />
        </ul>

        <p className="mt-8 text-center text-[11.5px] leading-relaxed text-muted">
          앱스토어·플레이스토어에 올리지 않아요. 브라우저 표준(PWA) 설치라 심사·비용·계정이 없습니다.
        </p>
      </div>
    </div>
  );
}

function PrimaryAction({
  platform,
  canInstall,
  onInstall,
}: {
  platform: Platform | null;
  canInstall: boolean;
  onInstall: () => void;
}) {
  // iOS: beforeinstallprompt 미지원 → 공유 → 홈 화면에 추가 안내(정책상 유일 경로).
  if (platform === "ios") {
    return (
      <div className="mt-8 w-full rounded-2xl border border-line-cream bg-white px-5 py-5">
        <div className="text-[14px] font-extrabold text-ink">iPhone·iPad에 추가하기</div>
        <ol className="mt-4 flex flex-col gap-3">
          <Step n={1}>
            사파리 하단의 <span className="inline-flex items-center gap-1 font-bold text-teal-deep">공유 <Icon name="share" size={14} /></span> 버튼을 누르세요.
          </Step>
          <Step n={2}>
            메뉴에서 <b className="text-ink-2">‘홈 화면에 추가’</b>를 선택하세요.
          </Step>
          <Step n={3}>
            오른쪽 위 <b className="text-ink-2">‘추가’</b>를 누르면 홈 화면 아이콘으로 전체화면 실행돼요.
          </Step>
        </ol>
      </div>
    );
  }

  // 안드로이드/데스크톱: 설치 프롬프트가 캡처됐으면 원탭 설치.
  if (canInstall) {
    return (
      <button
        type="button"
        onClick={onInstall}
        className="mt-8 flex w-full items-center justify-center gap-2 rounded-2xl bg-forest px-5 py-4 text-[15px] font-extrabold text-white transition-transform active:scale-[0.99]"
      >
        <DownloadIcon />
        홈 화면에 앱 설치
      </button>
    );
  }

  // 안드로이드지만 아직 프롬프트 미도착: 메뉴 경로 안내(프롬프트 도착 시 위 버튼으로 승격).
  if (platform === "android") {
    return (
      <div className="mt-8 w-full rounded-2xl border border-line-cream bg-white px-5 py-5 text-[13.5px] leading-relaxed text-ink-2">
        <div className="text-[14px] font-extrabold text-ink">Android에 추가하기</div>
        <p className="mt-3">
          Chrome 우측 상단 <b>⋮</b> 메뉴 → <b className="text-teal-deep">‘앱 설치’</b>(또는 ‘홈 화면에 추가’)를 누르세요.
          잠시 뒤 이 화면에 <b>원탭 설치 버튼</b>이 나타나기도 해요.
        </p>
      </div>
    );
  }

  // 데스크톱(설치 프롬프트 없음): 휴대폰 유도.
  return (
    <div className="mt-8 w-full rounded-2xl border border-line-cream bg-white px-5 py-5 text-[13.5px] leading-relaxed text-ink-2">
      <div className="text-[14px] font-extrabold text-ink">휴대폰에서 설치하세요</div>
      <p className="mt-3">
        <b className="text-teal-deep">geuneul.vercel.app/install</b> 을 휴대폰 Chrome(안드로이드) 또는 Safari(iPhone)로 열면
        홈 화면에 설치할 수 있어요. 데스크톱 Chrome이라면 주소창 오른쪽 설치 아이콘을 눌러도 돼요.
      </p>
    </div>
  );
}

function Feature({ icon, title, desc }: { icon: IconName; title: string; desc: string }) {
  return (
    <li className="flex flex-col gap-1.5 rounded-2xl border border-line-cream bg-white px-4 py-3.5">
      <span className="flex h-8 w-8 items-center justify-center rounded-full bg-mint text-forest">
        <Icon name={icon} size={16} />
      </span>
      <span className="text-[13px] font-extrabold text-ink">{title}</span>
      <span className="text-[11.5px] text-ink-3">{desc}</span>
    </li>
  );
}

function Step({ n, children }: { n: number; children: React.ReactNode }) {
  return (
    <li className="flex items-start gap-3">
      <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-forest text-[11px] font-extrabold text-white">
        {n}
      </span>
      <span className="text-[13px] leading-relaxed text-ink-2">{children}</span>
    </li>
  );
}

function DownloadIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path d="M12 3v12m0 0 4-4m-4 4-4-4" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden>
      <path d="m5 13 4 4L19 7" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
