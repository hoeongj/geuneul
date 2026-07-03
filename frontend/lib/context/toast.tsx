"use client";

import { createContext, useCallback, useContext, useRef, useState } from "react";

interface ToastState {
  message: string | null;
  show: (message: string) => void;
}

const ToastCtx = createContext<ToastState | null>(null);

// 하단 중앙 토스트(현재위치/길찾기/공유/제출 피드백). ~1.7s 후 자동 소멸.
export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [message, setMessage] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const show = useCallback((msg: string) => {
    setMessage(msg);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => setMessage(null), 1700);
  }, []);

  return <ToastCtx.Provider value={{ message, show }}>{children}</ToastCtx.Provider>;
}

export function useToast(): ToastState {
  const ctx = useContext(ToastCtx);
  if (!ctx) throw new Error("useToast must be used within ToastProvider");
  return ctx;
}
