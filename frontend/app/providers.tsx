"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { GeoProvider } from "@/lib/context/geo";
import { SelectedPlaceProvider } from "@/lib/context/selected";
import { SelectedUserProvider } from "@/lib/context/selectedUser";
import { ToastProvider } from "@/lib/context/toast";

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // 지도/리스트 재조회 과호출 방지 + 모바일 배터리 배려.
            refetchOnWindowFocus: false,
            retry: 1,
            staleTime: 15_000,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <GeoProvider>
        <ToastProvider>
          <SelectedPlaceProvider>
            <SelectedUserProvider>{children}</SelectedUserProvider>
          </SelectedPlaceProvider>
        </ToastProvider>
      </GeoProvider>
    </QueryClientProvider>
  );
}
