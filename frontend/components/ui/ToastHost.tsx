"use client";

import { useToast } from "@/lib/context/toast";

// 하단 중앙 토스트. rgba(22,36,29,.94) / 글자 #F5F3EC, 페이드업 ~1.7s.
export function ToastHost() {
  const { message } = useToast();
  if (!message) return null;
  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-5 z-50 flex justify-center px-6">
      <div
        className="gn-toast max-w-full rounded-full px-4 py-2.5 text-[13px] font-semibold text-cream shadow-lg"
        style={{ background: "rgba(22,36,29,.94)" }}
        role="status"
        aria-live="polite"
      >
        {message}
      </div>
    </div>
  );
}
