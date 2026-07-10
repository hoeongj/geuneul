// 알림(B1, ADR-0018) — 백엔드 /notifications(센터)·/notifications/rules(규칙). 로그인 유저 개인화(살).
export type NotificationRuleType = "SURGE_NEARBY" | "BOOKMARK_SURGE" | "HEAT_ESCAPE";

export interface NotificationRule {
  id: number;
  type: NotificationRuleType;
  centerLat: number | null;
  centerLng: number | null;
  radiusM: number | null;
  active: boolean;
  createdAt: string;
}

export interface NotificationItem {
  id: number;
  type: NotificationRuleType;
  title: string;
  body: string; // §6 순화 문구(백엔드 생성)
  placeId: number | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationCenter {
  unread: number;
  items: NotificationItem[];
}
