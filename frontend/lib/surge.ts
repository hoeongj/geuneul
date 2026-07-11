"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import type { SurgeInfo } from "@/types/alert";
import type { MapBounds } from "@/types/place";
import { fetchSurge } from "./api";

function within(bounds: MapBounds | null, lat: number, lng: number): boolean {
  if (!bounds) return false;
  return lat >= bounds.south && lat <= bounds.north && lng >= bounds.west && lng <= bounds.east;
}

/**
 * 실시간 제보 급증 구독(A4, 백엔드 ADR-0016). 두 경로를 합친다:
 *  - **스냅샷/폴백**: GET /api/alerts/surge?bounds= 를 react-query로 주기 폴링(45s) — SSE 미지원·재연결
 *    공백을 메우는 신뢰 baseline.
 *  - **실시간 푸시**: EventSource(/api/alerts/stream)의 name="surge" 이벤트 — 감지 즉시 반영. 끊기면
 *    EventSource가 자동 재연결한다(백오프 내장).
 *
 * 병합은 placeId 기준 dedupe(라이브가 스냅샷을 덮어씀=최신) 후 현재 뷰포트로 필터한다. onNew는 뷰포트
 * 안의 새 급증에만 호출한다(지도 밖 급증으로 토스트가 튀지 않게). 표현은 §6대로 백엔드가 순화한 message를 쓴다.
 */
export function useSurgeAlerts(bounds: MapBounds | null, onNew?: (s: SurgeInfo) => void) {
  const snapshot = useQuery({
    queryKey: ["surge", bounds],
    queryFn: () => fetchSurge(bounds!),
    enabled: !!bounds,
    refetchInterval: 45_000,
    staleTime: 20_000,
  });

  const [live, setLive] = useState<SurgeInfo[]>([]);
  // 최신 onNew·bounds를 effect(SSE 콜백)에서 참조하기 위한 ref — 갱신은 render가 아니라 effect에서 한다.
  const onNewRef = useRef(onNew);
  const boundsRef = useRef(bounds);
  useEffect(() => {
    onNewRef.current = onNew;
    boundsRef.current = bounds;
  });

  useEffect(() => {
    if (typeof window === "undefined" || typeof EventSource === "undefined") return;
    const es = new EventSource("/api/alerts/stream");
    es.addEventListener("surge", (evt) => {
      try {
        const info = JSON.parse((evt as MessageEvent).data) as SurgeInfo;
        setLive((prev) => [info, ...prev.filter((p) => p.placeId !== info.placeId)].slice(0, 20));
        if (within(boundsRef.current, info.lat, info.lng)) onNewRef.current?.(info);
      } catch {
        /* 잘못된 이벤트 페이로드는 무시(재연결 heartbeat 등) */
      }
    });
    // onerror 시 EventSource가 자동 재연결하므로 별도 처리 불필요(닫지 않는다).
    return () => es.close();
  }, []);

  const surges = useMemo(() => {
    const map = new Map<number, SurgeInfo>();
    for (const s of snapshot.data ?? []) map.set(s.placeId, s);
    for (const s of live) map.set(s.placeId, s); // 라이브가 최신 → 스냅샷을 덮어씀
    return [...map.values()].filter((s) => within(bounds, s.lat, s.lng));
  }, [snapshot.data, live, bounds]);

  return { surges };
}
