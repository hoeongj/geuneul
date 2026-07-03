"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { MapBounds, ReportCreatePayload, Scenario } from "@/types/place";
import {
  createReport,
  fetchByBounds,
  fetchByRadius,
  fetchNearestAny,
  fetchPlace,
  fetchPlaceReports,
  fetchUrgent,
} from "./api";

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

// 제보 기본 장소 = 현재 위치 최근접 1곳.
export function useNearestPlace(coords: { lat: number; lng: number } | null) {
  return useQuery({
    queryKey: ["nearest-any", coords?.lat, coords?.lng],
    queryFn: () => fetchNearestAny({ lat: coords!.lat, lng: coords!.lng, limit: 1 }),
    enabled: !!coords,
    select: (places) => places[0] ?? null,
    staleTime: 30_000,
  });
}

// 장소 상세 "최근 제보" — 휘발성이라 staleTime 짧게.
export function usePlaceReports(placeId: number | null) {
  return useQuery({
    queryKey: ["reports", placeId],
    queryFn: () => fetchPlaceReports(placeId!),
    enabled: placeId != null,
    staleTime: 10_000,
  });
}

// 제보 생성 — 성공 시 해당 장소의 제보 목록을 즉시 갱신.
export function useCreateReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReportCreatePayload) => createReport(payload),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ["reports", created.placeId] });
    },
  });
}
