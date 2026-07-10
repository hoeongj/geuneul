package com.geuneul.domain.notification;

/**
 * 알림 규칙 종류(B1, ADR-0018).
 * <ul>
 *   <li>{@link #SURGE_NEARBY} — 내 주변(center+radius_m) 제보 급증. 급증 이벤트 평가.</li>
 *   <li>{@link #BOOKMARK_SURGE} — 내가 저장한 장소(A7)의 급증("관심 장소 상태 변화"). 급증 이벤트 평가.</li>
 *   <li>{@link #HEAT_ESCAPE} — 폭염 피난 추천(날씨 트리거). MVP 범위 밖(타입만 정의, 평가는 follow-up).</li>
 * </ul>
 */
public enum NotificationRuleType {
    SURGE_NEARBY,
    BOOKMARK_SURGE,
    HEAT_ESCAPE
}
