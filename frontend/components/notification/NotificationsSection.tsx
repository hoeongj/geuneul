"use client";

import { useEffect, useState } from "react";
import { useGeo } from "@/lib/context/geo";
import { useToast } from "@/lib/context/toast";
import { DEFAULT_RADIUS } from "@/lib/geo";
import { getPushStatus, isPushSupported, isSubscribed, sendTestPush, subscribeToPush } from "@/lib/push";
import {
  useMarkNotificationsRead,
  useNotificationRules,
  useNotifications,
  useToggleNotificationRule,
} from "@/lib/queries";
import { formatRelativeTime } from "@/lib/reports";
import type { NotificationRuleType } from "@/types/notification";

// 기기 푸시(F2) — 앱-내 알림 규칙과 별개로, OS 배너를 받으려면 이 기기를 구독해야 한다.
// 설치형 PWA(홈 화면 추가)에서만 iOS 배너가 뜬다(ADR-0018). 서버 비활성(키 없음)이면 행 자체를 숨긴다.
function PushControls() {
  const { show } = useToast();
  const [state, setState] = useState<"loading" | "available" | "subscribed" | "hidden">("loading");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let alive = true;
    (async () => {
      if (!isPushSupported()) return setState("hidden");
      const status = await getPushStatus();
      if (!alive) return;
      if (!status.enabled) return setState("hidden"); // 서버 push 미활성
      setState((await isSubscribed()) ? "subscribed" : "available");
    })();
    return () => {
      alive = false;
    };
  }, []);

  if (state === "hidden" || state === "loading") return null;

  const onSubscribe = async () => {
    if (busy) return;
    setBusy(true);
    const r = await subscribeToPush();
    setBusy(false);
    if (r === "ok") {
      setState("subscribed");
      show("기기 푸시를 켰어요");
    } else if (r === "denied") {
      show("알림 권한이 거부됐어요. 브라우저 설정에서 허용해 주세요");
    } else {
      show("기기 푸시 설정에 실패했어요");
    }
  };

  const onTest = async () => {
    if (busy) return;
    setBusy(true);
    const ok = await sendTestPush();
    setBusy(false);
    show(ok ? "테스트 알림을 보냈어요" : "테스트 발송에 실패했어요");
  };

  return (
    <div className="flex items-center justify-between gap-3 px-4 py-3">
      <div className="min-w-0">
        <div className="text-[13.5px] font-bold text-ink">기기 푸시 알림</div>
        <div className="truncate text-[12px] text-ink-3">
          {state === "subscribed" ? "이 기기로 배너 알림을 받아요" : "앱을 닫아도 OS 알림으로 받아요"}
        </div>
      </div>
      {state === "subscribed" ? (
        <button
          type="button"
          onClick={onTest}
          disabled={busy}
          className="shrink-0 rounded-full border border-line-chip px-3 py-1.5 text-[12px] font-bold text-teal disabled:opacity-50"
        >
          테스트
        </button>
      ) : (
        <button
          type="button"
          onClick={onSubscribe}
          disabled={busy}
          className="shrink-0 rounded-full bg-forest px-3 py-1.5 text-[12px] font-bold text-cream disabled:opacity-50"
        >
          켜기
        </button>
      )}
    </div>
  );
}

// 알림 규칙 토글 한 줄 — 규칙이 있으면 삭제, 없으면 생성. SURGE_NEARBY는 현재 위치+반경으로 만든다.
function RuleToggle({
  type,
  label,
  desc,
}: {
  type: NotificationRuleType;
  label: string;
  desc: string;
}) {
  const { show } = useToast();
  const geo = useGeo();
  const { data: rules } = useNotificationRules(true);
  const toggle = useToggleNotificationRule();
  const existing = rules?.find((r) => r.type === type);
  const on = !!existing;

  const onClick = () => {
    if (toggle.isPending) return;
    if (on) {
      toggle.mutate({ action: "delete", id: existing!.id }, { onSuccess: () => show("알림을 껐어요") });
    } else if (type === "SURGE_NEARBY") {
      toggle.mutate(
        { action: "create", type, lat: geo.lat, lng: geo.lng, radiusM: DEFAULT_RADIUS },
        {
          onSuccess: () => show("내 주변 급증 알림을 켰어요"),
          onError: () => show("알림 설정에 실패했어요"),
        },
      );
    } else if (type === "HEAT_ESCAPE") {
      // 폭염 피난은 현재 위치를 판정 중심으로 쓴다(반경 없음). 알림 센터를 열 때 온디맨드 평가된다(ADR-0020).
      toggle.mutate(
        { action: "create", type, lat: geo.lat, lng: geo.lng },
        {
          onSuccess: () => show("폭염 피난 알림을 켰어요"),
          onError: () => show("알림 설정에 실패했어요"),
        },
      );
    } else {
      toggle.mutate(
        { action: "create", type },
        { onSuccess: () => show("알림을 켰어요"), onError: () => show("알림 설정에 실패했어요") },
      );
    }
  };

  return (
    <div className="flex items-center justify-between gap-3 px-4 py-3">
      <div className="min-w-0">
        <div className="text-[13.5px] font-bold text-ink">{label}</div>
        <div className="truncate text-[12px] text-ink-3">{desc}</div>
      </div>
      <button
        type="button"
        onClick={onClick}
        aria-pressed={on}
        disabled={toggle.isPending}
        className={
          "relative h-6 w-11 shrink-0 rounded-full transition-colors disabled:opacity-50 " +
          (on ? "bg-forest" : "bg-line-chip")
        }
      >
        <span
          className={
            "absolute top-0.5 h-5 w-5 rounded-full bg-white transition-all " + (on ? "left-[22px]" : "left-0.5")
          }
        />
      </button>
    </div>
  );
}

// 알림(B1) — 마이페이지 섹션. 알림 센터(발송 이력·모두읽음) + 규칙 토글 2종(내 주변 급증·관심 장소 급증).
// 표현은 §6대로 백엔드가 순화한 문구를 그대로 쓴다(공포 조장 금지). 개인화(살) — 간판과 무연결.
export function NotificationsSection() {
  const { data, isLoading } = useNotifications(true);
  const markRead = useMarkNotificationsRead();
  const items = data?.items ?? [];
  const unread = data?.unread ?? 0;

  return (
    <div className="overflow-hidden rounded-[14px] border border-line-cream bg-white">
      <div className="flex items-center gap-2 border-b border-line-cream px-4 py-3">
        <h2 className="text-[14px] font-extrabold text-ink-2">알림</h2>
        {unread > 0 && (
          <span className="rounded-full bg-forest px-1.5 py-0.5 text-[10px] font-bold text-cream">{unread}</span>
        )}
        <div className="flex-1" />
        {unread > 0 && (
          <button
            type="button"
            onClick={() => markRead.mutate()}
            className="text-[12px] font-semibold text-teal"
          >
            모두 읽음
          </button>
        )}
      </div>

      {/* 규칙 토글 */}
      <RuleToggle type="SURGE_NEARBY" label="내 주변 제보 급증" desc="현재 위치 반경에서 제보가 몰리면 알려줘요" />
      <div className="border-t border-line-white-2" />
      <RuleToggle type="BOOKMARK_SURGE" label="관심 장소 소식" desc="저장한 장소에 제보가 몰리면 알려줘요" />
      <div className="border-t border-line-white-2" />
      <RuleToggle type="HEAT_ESCAPE" label="폭염 피난 추천" desc="더울 때 가까운 무더위쉼터로 안내해요" />
      <div className="border-t border-line-white-2" />
      <PushControls />

      {/* 알림 센터 */}
      <div className="border-t border-line-cream">
        {isLoading ? (
          <p className="px-4 py-4 text-[13px] text-muted">불러오는 중…</p>
        ) : items.length === 0 ? (
          <p className="px-4 py-4 text-[13px] text-muted">아직 받은 알림이 없어요</p>
        ) : (
          <ul>
            {items.map((n) => (
              <li
                key={n.id}
                className={
                  "border-b border-line-white-2 px-4 py-3 last:border-none " + (n.read ? "" : "bg-mint-3/40")
                }
              >
                <div className="flex items-baseline justify-between gap-2">
                  <span className="text-[13px] font-bold text-ink">{n.title}</span>
                  <span className="shrink-0 text-[11px] text-muted">{formatRelativeTime(n.createdAt)}</span>
                </div>
                <p className="mt-0.5 text-[12.5px] text-ink-3">{n.body}</p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
