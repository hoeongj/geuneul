"use client";

import { createContext, useCallback, useContext, useState } from "react";

// 작성자 공개 프로필(N7)도 라우트가 아니라 오버레이로 연다(장소 상세와 동일 패턴, 탭바 유지).
interface SelectedUserState {
  id: number | null;
  open: (userId: number) => void;
  close: () => void;
}

const SelectedUserCtx = createContext<SelectedUserState | null>(null);

export function SelectedUserProvider({ children }: { children: React.ReactNode }) {
  const [id, setId] = useState<number | null>(null);
  const open = useCallback((userId: number) => setId(userId), []);
  const close = useCallback(() => setId(null), []);
  return <SelectedUserCtx.Provider value={{ id, open, close }}>{children}</SelectedUserCtx.Provider>;
}

export function useSelectedUser(): SelectedUserState {
  const ctx = useContext(SelectedUserCtx);
  if (!ctx) throw new Error("useSelectedUser must be used within SelectedUserProvider");
  return ctx;
}
