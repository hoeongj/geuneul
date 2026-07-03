"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { MapBounds, Scenario } from "@/types/place";
import { fetchByBounds, fetchByRadius, fetchPlace, fetchUrgent } from "./api";

// 뷰포트 이동(bounds) 시 마커 재조회. keepPreviousData 로 패닝 중 깜빡임 방지 → 과호출/리렌더 최소화.
export function useBoundsPlaces(bounds: MapBounds | null) {
  return useQuery({
    queryKey: ["places", "bounds", bounds],
    queryFn: () => fetchByBounds(bounds!),
    enabled: !!bounds,
    placeholderData: keepPreviousData,
    staleTime: 15_000,
  });
}

// 현재 위치 반경 리스트(바텀시트).
export function useRadiusPlaces(coords: { lat: number; lng: number } | null, radius: number) {
  return useQuery({
    queryKey: ["places", "radius", coords?.lat, coords?.lng, radius],
    queryFn: () => fetchByRadius({ lat: coords!.lat, lng: coords!.lng, radius }),
    enabled: !!coords,
    placeholderData: keepPreviousData,
    staleTime: 15_000,
  });
}

export function usePlace(id: number | null) {
  return useQuery({
    queryKey: ["place", id],
    queryFn: () => fetchPlace(id!),
    enabled: id != null,
    staleTime: 60_000,
  });
}

export function useUrgent(scenario: Scenario | null, coords: { lat: number; lng: number } | null) {
  return useQuery({
    queryKey: ["urgent", scenario, coords?.lat, coords?.lng],
    queryFn: () => fetchUrgent({ scenario: scenario!, lat: coords!.lat, lng: coords!.lng }),
    enabled: !!scenario && !!coords,
    staleTime: 15_000,
  });
}
