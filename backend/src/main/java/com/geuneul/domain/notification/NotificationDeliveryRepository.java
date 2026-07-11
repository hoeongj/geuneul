package com.geuneul.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationDeliveryRepository
        extends JpaRepository<NotificationDelivery, Long>, NotificationDeliveryRepositoryCustom {

    /** 알림 센터 — 유저 최신순(idx_notification_deliveries_user). 최근 50건이면 충분. */
    List<NotificationDelivery> findTop50ByUserIdOrderByCreatedAtDesc(long userId);

    long countByUserIdAndReadFalse(long userId);

    /**
     * SURGE_NEARBY 규칙 매칭 발송 — 급증 장소가 규칙 중심에서 radius_m 안이면 활성 규칙별로 발송 이력 1건.
     * ST_DWithin(간판 공간쿼리 재사용)로 매칭하고, dedup_key UNIQUE + ON CONFLICT DO NOTHING으로
     * 멀티 인스턴스 중복 + cooldown(bucket)을 막는다. @return 실제 삽입된 행 수.
     */
    @Modifying
    @Query(value = """
            INSERT INTO notification_deliveries (user_id, rule_id, type, title, body, place_id, dedup_key)
            SELECT r.user_id, r.id, 'SURGE_NEARBY', :title, :body, :placeId,
                   'surge:' || r.id || ':' || :placeId || ':' || :bucket
            FROM notification_rules r
            WHERE r.is_active AND r.type = 'SURGE_NEARBY'
              AND r.center_lat IS NOT NULL AND r.center_lng IS NOT NULL AND r.radius_m IS NOT NULL
              AND ST_DWithin(
                    geography(ST_SetSRID(ST_MakePoint(r.center_lng, r.center_lat), 4326)),
                    geography(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)),
                    r.radius_m)
            ON CONFLICT (dedup_key) DO NOTHING
            """, nativeQuery = true)
    int insertSurgeNearby(@Param("placeId") long placeId, @Param("lat") double lat, @Param("lng") double lng,
                          @Param("title") String title, @Param("body") String body, @Param("bucket") long bucket);

    /**
     * BOOKMARK_SURGE 규칙 매칭 발송 — 급증 장소를 북마크한 유저(A7)의 활성 규칙별로 발송. dedup 동일.
     * @return 실제 삽입된 행 수.
     */
    @Modifying
    @Query(value = """
            INSERT INTO notification_deliveries (user_id, rule_id, type, title, body, place_id, dedup_key)
            SELECT r.user_id, r.id, 'BOOKMARK_SURGE', :title, :body, :placeId,
                   'bmsurge:' || r.id || ':' || :placeId || ':' || :bucket
            FROM notification_rules r
            JOIN bookmarks b ON b.user_id = r.user_id AND b.place_id = :placeId
            WHERE r.is_active AND r.type = 'BOOKMARK_SURGE'
            ON CONFLICT (dedup_key) DO NOTHING
            """, nativeQuery = true)
    int insertBookmarkSurge(@Param("placeId") long placeId,
                            @Param("title") String title, @Param("body") String body, @Param("bucket") long bucket);

    // BOOKMARK_STATUS 발송(C3, ADR-0026)은 INSERT ... RETURNING user_id가 필요해 커스텀 조각으로 분리했다
    // (NotificationDeliveryRepositoryCustom#insertBookmarkStatusReturning) — 이 트랜잭션이 실제 삽입한 유저만 푸시해
    // 사전 SELECT 스냅샷과 실제 삽입 집합의 괴리(누락·중복 푸시)를 없앤다.

    /**
     * HEAT_ESCAPE 발송 — 폭염 확인 시 규칙별 1건(ADR-0020). 급증과 달리 날씨·쉼터가 Java 계산이라
     * 매칭 없는 단일행 INSERT다. dedup_key(heat:ruleId:bucket) UNIQUE + ON CONFLICT DO NOTHING으로
     * 멀티 인스턴스 중복 + cooldown(bucket) + 반복 열람을 함께 막는다. @return 실제 삽입된 행 수(0 또는 1).
     */
    @Modifying
    @Query(value = """
            INSERT INTO notification_deliveries (user_id, rule_id, type, title, body, place_id, dedup_key)
            VALUES (:userId, :ruleId, 'HEAT_ESCAPE', :title, :body, :placeId, :dedupKey)
            ON CONFLICT (dedup_key) DO NOTHING
            """, nativeQuery = true)
    int insertHeatEscape(@Param("userId") long userId, @Param("ruleId") long ruleId,
                         @Param("placeId") long placeId, @Param("title") String title,
                         @Param("body") String body, @Param("dedupKey") String dedupKey);

    /** 읽음 처리 — 내 알림만(user_id 게이트). id가 null이면 전체 읽음. @return 갱신 행 수. */
    @Modifying
    @Query("UPDATE NotificationDelivery d SET d.read = true "
            + "WHERE d.userId = :userId AND d.read = false AND (:id IS NULL OR d.id = :id)")
    int markRead(@Param("userId") long userId, @Param("id") Long id);
}
