package com.geuneul.domain.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, Long> {

    /** 발송 fan-out — 유저의 모든 기기 구독. */
    List<PushSubscriptionEntity> findByUserId(long userId);

    /**
     * 구독 upsert — 같은 endpoint(기기)가 재구독하면 소유자·키를 갱신(중복 방지). @return 영향 행 수.
     * endpoint UNIQUE + ON CONFLICT DO UPDATE로 재구독/소유자 변경(로그아웃→다른 계정)을 안전히 처리한다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth)
            VALUES (:userId, :endpoint, :p256dh, :auth)
            ON CONFLICT (endpoint) DO UPDATE
              SET user_id = EXCLUDED.user_id, p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth
            """, nativeQuery = true)
    int upsert(@Param("userId") long userId, @Param("endpoint") String endpoint,
               @Param("p256dh") String p256dh, @Param("auth") String auth);

    /** 만료·해지된 구독 정리(push 서비스가 404/410 반환 시 호출). */
    @Modifying
    @Query(value = "DELETE FROM push_subscriptions WHERE endpoint = :endpoint", nativeQuery = true)
    int deleteByEndpoint(@Param("endpoint") String endpoint);
}
