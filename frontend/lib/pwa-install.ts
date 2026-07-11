"use client";

// PWA 설치 캡처 싱글턴 — 안드로이드 Chrome/Edge가 설치 가능 판정 시 발화하는 `beforeinstallprompt`를
// 앱 어디서든(홈/직접진입/SPA 이동) 놓치지 않으려 root Providers에서 initPwaInstall()로 전역 리스너를
// 조기 등록해 이벤트를 보관한다. /install 페이지가 이 보관분으로 원탭 설치를 띄운다(WebAPK, D2).
//
// iOS는 beforeinstallprompt가 없다(정책) → 설치는 "공유 → 홈 화면에 추가"뿐이라 UI에서 안내로 분기한다.

type BeforeInstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
};

let deferred: BeforeInstallPromptEvent | null = null;
let installed = false;
let started = false;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((l) => l());
}

// 전역 리스너 조기 등록(1회). Providers 마운트 시 호출 → 어느 페이지로 들어와도 캡처.
export function initPwaInstall() {
  if (started || typeof window === "undefined") return;
  started = true;
  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault(); // 브라우저 기본 미니-인포바 억제 → 우리 버튼으로 유도
    deferred = e as BeforeInstallPromptEvent;
    emit();
  });
  window.addEventListener("appinstalled", () => {
    installed = true;
    deferred = null;
    emit();
  });
}

export function subscribeInstall(cb: () => void): () => void {
  listeners.add(cb);
  return () => {
    listeners.delete(cb);
  };
}

export function canPromptInstall(): boolean {
  return deferred !== null;
}

export function isAppInstalled(): boolean {
  if (installed) return true;
  if (typeof window === "undefined") return false;
  const standalone = window.matchMedia?.("(display-mode: standalone)").matches ?? false;
  // iOS Safari 설치형은 navigator.standalone.
  const iosStandalone = (window.navigator as unknown as { standalone?: boolean }).standalone === true;
  return standalone || iosStandalone;
}

// 원탭 설치(WebAPK). 캡처된 프롬프트가 없으면 unavailable(iOS/데스크톱/아직 미판정).
export async function promptInstall(): Promise<"accepted" | "dismissed" | "unavailable"> {
  if (!deferred) return "unavailable";
  await deferred.prompt();
  const { outcome } = await deferred.userChoice;
  deferred = null; // 프롬프트는 1회용
  emit();
  return outcome;
}

export type Platform = "ios" | "android" | "desktop";

export function detectPlatform(): Platform {
  if (typeof navigator === "undefined") return "desktop";
  const ua = navigator.userAgent;
  // iPadOS 13+는 Macintosh로 보고하므로 터치 지원으로 보정.
  const isIpad = /macintosh/i.test(ua) && typeof document !== "undefined" && "ontouchend" in document;
  if (/iphone|ipad|ipod/i.test(ua) || isIpad) return "ios";
  if (/android/i.test(ua)) return "android";
  return "desktop";
}
