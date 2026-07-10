// Web Push(F2) 클라이언트 — 권한 요청 + 구독 + 백엔드 등록. 설치형 PWA(홈 화면 추가)에서만 iOS 배너가 뜬다(ADR-0018).

export interface PushStatus {
  enabled: boolean; // 서버 push 활성(VAPID 키 있음)
  publicKey: string; // applicationServerKey(base64url)
}

// 이 브라우저가 Web Push를 지원하는가(iOS는 설치형 PWA에서만 pushManager 노출).
export function isPushSupported(): boolean {
  return (
    typeof window !== "undefined" &&
    "serviceWorker" in navigator &&
    "PushManager" in window &&
    "Notification" in window
  );
}

// base64url VAPID 공개키 → Uint8Array(applicationServerKey 형식).
// 명시적 ArrayBuffer로 만들어 BufferSource(ArrayBuffer 백킹) 타입을 만족시킨다(lib.dom strict).
function urlBase64ToUint8Array(base64String: string): Uint8Array<ArrayBuffer> {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64);
  const buffer = new ArrayBuffer(raw.length);
  const out = new Uint8Array(buffer);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

export async function getPushStatus(): Promise<PushStatus> {
  const res = await fetch("/api/push/public-key");
  if (!res.ok) return { enabled: false, publicKey: "" };
  return (await res.json()) as PushStatus;
}

// 이미 이 기기가 구독돼 있는지.
export async function isSubscribed(): Promise<boolean> {
  if (!isPushSupported()) return false;
  const reg = await navigator.serviceWorker.ready;
  return (await reg.pushManager.getSubscription()) != null;
}

/**
 * 푸시 구독: 권한 요청 → pushManager.subscribe(applicationServerKey) → 백엔드 등록.
 * @returns "ok" | "denied"(권한 거부) | "unsupported" | "disabled"(서버 비활성) | "error"
 */
export async function subscribeToPush(): Promise<"ok" | "denied" | "unsupported" | "disabled" | "error"> {
  if (!isPushSupported()) return "unsupported";
  const status = await getPushStatus();
  if (!status.enabled || !status.publicKey) return "disabled";

  const permission = await Notification.requestPermission();
  if (permission !== "granted") return "denied";

  try {
    const reg = await navigator.serviceWorker.ready;
    const sub =
      (await reg.pushManager.getSubscription()) ??
      (await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(status.publicKey),
      }));

    const res = await fetch("/api/push/subscribe", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(sub.toJSON()),
    });
    return res.ok ? "ok" : "error";
  } catch {
    return "error";
  }
}

// 내 기기로 테스트 배너 1회(구독 후 end-to-end 확인용).
export async function sendTestPush(): Promise<boolean> {
  const res = await fetch("/api/push/test", { method: "POST" });
  return res.ok;
}
