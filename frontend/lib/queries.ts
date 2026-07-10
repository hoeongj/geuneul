"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { NotificationRuleType } from "@/types/notification";
import type { MapBounds, ReportCreatePayload, Scenario } from "@/types/place";
import type { ReviewCreatePayload } from "@/types/review";
import {
  addBookmark,
  createNotificationRule,
  createReport,
  createReview,
  createReviewComment,
  deleteNotificationRule,
  fetchByBounds,
  fetchMyBookmarks,
  fetchNotificationRules,
  fetchNotifications,
  markNotificationsRead,
  fetchByRadius,
  fetchMe,
  fetchMyComments,
  fetchMyReactions,
  fetchMyReviews,
  fetchNearestAny,
  fetchPlace,
  fetchPlaceReports,
  fetchPlaceReviews,
  fetchPopularTimes,
  fetchReviewComments,
  fetchUrgent,
  logout,
  removeBookmark,
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

// 장소 상세 "시간대별 혼잡"(자체 popular-times). 이력 집계라 자주 안 변함 + 백엔드 Redis 1h 캐시 → staleTime 길게.
export function usePopularTimes(placeId: number | null) {
  return useQuery({
    queryKey: ["popular-times", placeId],
    queryFn: () => fetchPopularTimes(placeId!),
    enabled: placeId != null,
    staleTime: 10 * 60_000,
  });
}

// 장소 상세 "후기" — 영구 평판(survival_score와 분리, §5). 제보와 달리 휘발성이 아니라 staleTime을 길게 둔다.
export function usePlaceReviews(placeId: number | null) {
  return useQuery({
    queryKey: ["reviews", placeId],
    queryFn: () => fetchPlaceReviews(placeId!),
    enabled: placeId != null,
    staleTime: 60_000,
  });
}

// 후기 댓글 목록(2차·살, §0-9) — 펼쳤을 때만 지연 로드(enabled). 커뮤니티는 최소 표면만.
export function useReviewComments(reviewId: number, enabled: boolean) {
  return useQuery({
    queryKey: ["review-comments", reviewId],
    queryFn: () => fetchReviewComments(reviewId),
    enabled,
    staleTime: 30_000,
  });
}

// 후기 댓글 작성(로그인 필요) — 성공 시 해당 후기의 댓글 목록 갱신.
export function useCreateReviewComment(reviewId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (comment: string) => createReviewComment(reviewId, comment),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["review-comments", reviewId] }),
  });
}

// 후기 작성/수정(로그인 필요, 장소당 1건 upsert) — 성공 시 해당 장소의 후기 목록을 즉시 갱신.
export function useCreateReview() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReviewCreatePayload) => createReview(payload),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ["reviews", created.placeId] });
    },
  });
}

// 로그인 사용자 — null이면 로그아웃 상태. 세션은 httpOnly 쿠키라 클라는 /api/me로만 확인 가능.
export function useMe() {
  return useQuery({
    queryKey: ["me"],
    queryFn: fetchMe,
    staleTime: 60_000,
    retry: false,
  });
}

// 내 관심 장소 목록(로그인 시에만 조회 — 미로그인은 enabled=false로 401 회피).
export function useMyBookmarks(enabled: boolean) {
  return useQuery({
    queryKey: ["bookmarks"],
    queryFn: fetchMyBookmarks,
    enabled,
    staleTime: 30_000,
    retry: false,
  });
}

// 내 글 관리(N6) — 내 후기/댓글/유용해요(로그인 시에만). 마이페이지 "내 활동" 탭.
export function useMyReviews(enabled: boolean) {
  return useQuery({ queryKey: ["me-reviews"], queryFn: fetchMyReviews, enabled, staleTime: 30_000, retry: false });
}

export function useMyComments(enabled: boolean) {
  return useQuery({ queryKey: ["me-comments"], queryFn: fetchMyComments, enabled, staleTime: 30_000, retry: false });
}

export function useMyReactions(enabled: boolean) {
  return useQuery({ queryKey: ["me-reactions"], queryFn: fetchMyReactions, enabled, staleTime: 30_000, retry: false });
}

// 관심 장소 저장/해제 토글 — 성공 시 목록 캐시 무효화(상세 버튼·마이페이지가 함께 갱신).
export function useToggleBookmark() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ placeId, next }: { placeId: number; next: boolean }) =>
      next ? addBookmark(placeId) : removeBookmark(placeId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["bookmarks"] }),
  });
}

// 알림 센터(발송 이력) — 로그인 시에만. 폴링으로 새 알림을 주기 반영(60s).
export function useNotifications(enabled: boolean) {
  return useQuery({
    queryKey: ["notifications"],
    queryFn: fetchNotifications,
    enabled,
    staleTime: 30_000,
    refetchInterval: enabled ? 60_000 : false,
    retry: false,
  });
}

// 내 알림 규칙 — 로그인 시에만.
export function useNotificationRules(enabled: boolean) {
  return useQuery({
    queryKey: ["notification-rules"],
    queryFn: fetchNotificationRules,
    enabled,
    staleTime: 30_000,
    retry: false,
  });
}

// 알림 규칙 생성/삭제 토글 — 성공 시 규칙 목록 무효화.
type ToggleRuleArgs =
  | { action: "create"; type: NotificationRuleType; lat?: number; lng?: number; radiusM?: number }
  | { action: "delete"; id: number };

export function useToggleNotificationRule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (args: ToggleRuleArgs): Promise<void> => {
      if (args.action === "create") {
        await createNotificationRule({ type: args.type, lat: args.lat, lng: args.lng, radiusM: args.radiusM });
      } else {
        await deleteNotificationRule(args.id);
      }
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notification-rules"] }),
  });
}

// 알림 전체 읽음 — 성공 시 센터 무효화.
export function useMarkNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: markNotificationsRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });
}

// 로그아웃 — 쿠키 삭제 후 me 캐시를 null로.
export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.setQueryData(["me"], null);
    },
  });
}

// 제보 생성 — 성공 시 해당 장소의 제보 목록을 즉시 갱신.
export function useCreateReport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: ReportCreatePayload) => createReport(payload),
    onSuccess: (created) => {
      // 새 제보는 서버의 survival_score를 바꾼다 → 최근 제보 목록뿐 아니라
      // 상세 배지(place)·지도/리스트 마커(places)도 무효화해 "지금 상태"가 즉시 반영되게 한다.
      queryClient.invalidateQueries({ queryKey: ["reports", created.placeId] });
      queryClient.invalidateQueries({ queryKey: ["place", created.placeId] });
      queryClient.invalidateQueries({ queryKey: ["places"] });
    },
  });
}
