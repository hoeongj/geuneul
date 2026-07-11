package com.geuneul.domain.notification;

import java.util.List;

/**
 * BOOKMARK_STATUS 발송(C3, ADR-0026)의 RETURNING 삽입 — Spring Data @Query가 INSERT를 @Modifying(int) 전용으로
 * 취급할 수 있어(결과셋 반환 애매), EntityManager 네이티브 쿼리로 {@code INSERT ... RETURNING}을 직접 실행하는 커스텀 조각.
 */
public interface NotificationDeliveryRepositoryCustom {

    /**
     * 관심 장소 단건 상태 알림을 북마커의 활성 BOOKMARK_SURGE 규칙별로 삽입하고, <b>이 호출이 실제로 삽입한
     * 행의 user_id</b>를 돌려준다(ON CONFLICT DO NOTHING RETURNING). per-user dedup_key라 각 알림은 정확히 한
     * 호출·한 인스턴스만 삽입에 성공하므로, 반환 목록에만 푸시하면 멀티 인스턴스에서도 정확히 1회 푸시된다
     * (사전 SELECT 스냅샷과 실제 삽입 집합의 괴리로 생기던 누락·중복 푸시 제거 — C3 리뷰).
     */
    List<Long> insertBookmarkStatusReturning(long placeId, String reportType, String title, String body, long bucket);
}
