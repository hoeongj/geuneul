package com.geuneul.domain.notification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

/**
 * {@link NotificationDeliveryRepositoryCustom} 구현 — Spring Data 명명 규약(...RepositoryImpl)으로 자동 결합.
 * {@code INSERT ... ON CONFLICT DO NOTHING RETURNING user_id}를 EntityManager 네이티브 쿼리로 실행해,
 * 이 트랜잭션이 실제 삽입한 행의 user_id만 확실히 얻는다(Postgres는 INSERT...RETURNING을 결과셋으로 돌려줌).
 */
public class NotificationDeliveryRepositoryImpl implements NotificationDeliveryRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<Long> insertBookmarkStatusReturning(long placeId, String reportType, String title, String body,
                                                    long bucket) {
        // dedup_key 접두사 'bmstatus:'로 급증(bmsurge:)과 분리, type은 BOOKMARK_SURGE 재사용(§9 새 타입 금지).
        // reportType을 dedup_key에 넣어 FLOOD·SLIPPERY는 각각, 같은 타입 반복은 버킷 내 1건으로 묶는다.
        List<Number> ids = em.createNativeQuery("""
                INSERT INTO notification_deliveries (user_id, rule_id, type, title, body, place_id, dedup_key)
                SELECT r.user_id, r.id, 'BOOKMARK_SURGE', :title, :body, :placeId,
                       'bmstatus:' || r.id || ':' || :placeId || ':' || :reportType || ':' || :bucket
                FROM notification_rules r
                JOIN bookmarks b ON b.user_id = r.user_id AND b.place_id = :placeId
                WHERE r.is_active AND r.type = 'BOOKMARK_SURGE'
                ON CONFLICT (dedup_key) DO NOTHING
                RETURNING user_id
                """)
                .setParameter("placeId", placeId)
                .setParameter("reportType", reportType)
                .setParameter("title", title)
                .setParameter("body", body)
                .setParameter("bucket", bucket)
                .getResultList();
        return ids.stream().map(Number::longValue).toList();
    }
}
