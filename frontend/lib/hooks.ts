"use client";

import { useCallback, useEffect, useRef, useState, useSyncExternalStore, type RefObject } from "react";
import { presignPhoto, uploadPhotoToS3 } from "./api";
import { ALLOWED_PHOTO_TYPES, MAX_PHOTO_BYTES, type PhotoPurpose } from "@/types/photo";
import { canPromptInstall, isAppInstalled, subscribeInstall } from "./pwa-install";

// 뷰포트 idle 재조회 등 과호출 방지용 디바운스 콜백.
export function useDebouncedCallback<A extends unknown[]>(fn: (...args: A) => void, delay: number) {
  const fnRef = useRef(fn);
  useEffect(() => {
    fnRef.current = fn;
  }, [fn]);

  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  return useCallback(
    (...args: A) => {
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => fnRef.current(...args), delay);
    },
    [delay],
  );
}

export type PhotoUploadState = "idle" | "uploading" | "done" | "error";

/**
 * 제보/후기 사진 슬롯 공용 훅 — presign 발급 → S3 직접 PUT → objectUrl 확보까지 한 번에 처리한다
 * (report/page.tsx, ReviewsSection.tsx 둘 다 이 훅을 쓴다). purpose로 report/review를 구분(백엔드가
 * review는 로그인을 요구 — 401이면 error 상태로 메시지를 남긴다).
 */
export function usePhotoUpload(purpose: PhotoPurpose) {
  const [state, setState] = useState<PhotoUploadState>("idle");
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const previewObjectUrlRef = useRef<string | null>(null);
  const reqIdRef = useRef(0);

  const revokePreview = useCallback(() => {
    if (!previewObjectUrlRef.current) return;
    URL.revokeObjectURL(previewObjectUrlRef.current);
    previewObjectUrlRef.current = null;
  }, []);

  const reset = useCallback(() => {
    reqIdRef.current += 1;
    revokePreview();
    setState("idle");
    setPreviewUrl(null);
    setObjectUrl(null);
    setErrorMessage(null);
  }, [revokePreview]);

  useEffect(() => {
    return () => {
      reqIdRef.current += 1;
      revokePreview();
    };
  }, [revokePreview]);

  const pick = useCallback(
    async (file: File) => {
      const myId = ++reqIdRef.current;
      revokePreview();
      setPreviewUrl(null);
      setObjectUrl(null);
      if (!ALLOWED_PHOTO_TYPES.includes(file.type as (typeof ALLOWED_PHOTO_TYPES)[number])) {
        setState("error");
        setErrorMessage("jpg·png·webp 사진만 올릴 수 있어요");
        return;
      }
      if (file.size > MAX_PHOTO_BYTES) {
        setState("error");
        setErrorMessage("사진은 최대 8MB까지 올릴 수 있어요");
        return;
      }
      setState("uploading");
      setErrorMessage(null);
      const nextPreviewUrl = URL.createObjectURL(file);
      previewObjectUrlRef.current = nextPreviewUrl;
      setPreviewUrl(nextPreviewUrl);
      try {
        const presigned = await presignPhoto({ contentType: file.type, contentLength: file.size, purpose });
        if (reqIdRef.current !== myId) return;
        await uploadPhotoToS3(presigned, file);
        if (reqIdRef.current !== myId) return;
        setObjectUrl(presigned.objectUrl);
        setState("done");
      } catch {
        if (reqIdRef.current !== myId) return;
        setState("error");
        setErrorMessage(purpose === "review" ? "로그인 후 다시 시도해 주세요" : "사진 업로드에 실패했어요");
      }
    },
    [purpose, revokePreview],
  );

  return { state, previewUrl, objectUrl, errorMessage, pick, reset };
}

// 데스크톱(≥lg=1024px) 여부를 JS에서 판정 — Tailwind의 `lg:` 브레이크포인트(#92)와 정렬한다. matchMedia를
// useSyncExternalStore로 구독해(SSR 스냅샷=false) 하이드레이션 미스매치·set-state-in-effect 없이 반응한다.
// 용도: 오버레이 포커스 트랩 범위 판단(C2) — 데스크톱 지도 탭은 오버레이가 좌측 패널이라 하드 트랩을 끈다.
export function useIsLg() {
  return useSyncExternalStore(
    (onChange) => {
      const mql = window.matchMedia("(min-width: 1024px)");
      mql.addEventListener("change", onChange);
      return () => mql.removeEventListener("change", onChange);
    },
    () => window.matchMedia("(min-width: 1024px)").matches,
    () => false,
  );
}

/**
 * 오버레이(role=dialog) 공용 접근성 훅 — Esc 닫기 + 열림 시 패널 포커스 이동 + 닫힘 시 직전 포커스 복귀,
 * 그리고 trapTab일 때 Tab/Shift+Tab을 패널 안에서만 순환(포커스 트랩)한다. PlaceDetailOverlay·UserProfileOverlay가
 * 공유해 DRY(C2). 기존 두 오버레이의 Esc+focus-move+restore useEffect를 이 훅 하나로 통합했다.
 *
 * trapTab 판단은 호출부 몫이다: 데스크톱 지도 탭('/')에선 오버레이가 400px 좌측 패널이고 옆 지도·NavRail이
 * 살아 있어(뒤가 inert 아님) 하드 트랩하면 오히려 키보드 사용자를 보이는 UI에서 격리한다 → 그 경우만 false로 끈다.
 * 모바일 전 탭·데스크톱 비지도 탭은 오버레이가 전체를 덮으므로 트랩이 옳다.
 */
export function useDialogFocusTrap(
  panelRef: RefObject<HTMLElement | null>,
  active: boolean,
  close: () => void,
  options?: { trapTab?: boolean },
) {
  const trapTab = options?.trapTab ?? true;

  // 열릴 때 패널로 포커스 이동, 닫힐 때 직전 포커스 복귀(데스크톱 키보드 접근성, #94).
  useEffect(() => {
    if (!active) return;
    const prev = document.activeElement as HTMLElement | null;
    panelRef.current?.focus();
    return () => prev?.focus?.();
  }, [active, panelRef]);

  // Esc 닫기 + (trapTab이면) Tab 순환 가두기. 열려 있을 때만 리스너.
  useEffect(() => {
    if (!active) return;
    const onKey = (e: KeyboardEvent) => {
      // 중첩 모달(예: 상세 오버레이 위 신고 시트, aria-modal)이 이 패널 안에 열려 있으면 Esc·Tab을 그쪽이
      // 소유한다 — 부모 트랩은 양보한다(C1 리뷰: 한 번의 Esc가 부모까지 닫아 지도로 튕기던 버그). FlagSheet가
      // 이 panel의 DOM 자손이라(fixed여도 트리상 자손) querySelector로 깔끔히 감지된다.
      if (panelRef.current?.querySelector('[aria-modal="true"]')) return;
      if (e.key === "Escape") {
        close();
        return;
      }
      if (!trapTab || e.key !== "Tab") return;
      const panel = panelRef.current;
      if (!panel) return;
      // 포커서블 수집 + 가시 필터(offsetParent가 null이면 display:none/detached라 실제 탭 스톱 아님).
      const focusables = Array.from(
        panel.querySelectorAll<HTMLElement>(
          'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])',
        ),
      ).filter((el) => el.offsetParent !== null);
      if (focusables.length === 0) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      const cur = document.activeElement as HTMLElement | null;
      const inside = panel.contains(cur);
      if (e.shiftKey) {
        // 첫 요소·패널 컨테이너·바깥에서 역방향 Tab → 마지막으로 래핑.
        if (cur === first || cur === panel || !inside) {
          e.preventDefault();
          last.focus();
        }
      } else if (cur === last || !inside) {
        // 마지막 요소·바깥에서 정방향 Tab → 첫 요소로 래핑(패널 컨테이너는 브라우저가 first로 보냄).
        e.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [active, close, trapTab, panelRef]);
}

// PWA 설치 상태 구독(D2) — beforeinstallprompt 캡처 여부·이미 설치됨을 useSyncExternalStore로.
// 서버/최초 렌더는 false(하이드레이션 안전), 클라에서 이벤트 도착 시 리렌더.
export function useInstallState(): { canInstall: boolean; installed: boolean } {
  const canInstall = useSyncExternalStore(subscribeInstall, canPromptInstall, () => false);
  const installed = useSyncExternalStore(subscribeInstall, isAppInstalled, () => false);
  return { canInstall, installed };
}
