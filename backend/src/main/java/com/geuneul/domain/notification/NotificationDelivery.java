package com.geuneul.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 알림 발송 이력(B1, ADR-0018) — 규칙 충족 시 1건. dedup_key UNIQUE가 멀티 인스턴스 중복 + cooldown을 막는다.
 * 대부분의 쓰기는 서비스의 네이티브 매칭 INSERT(ON CONFLICT DO NOTHING)라, 이 엔티티는 읽기·읽음처리 위주로 쓴다.
 */
@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(nullable = false, length = 24)
    private String type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 300)
    private String body;

    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "dedup_key", nullable = false, length = 200)
    private String dedupKey;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected NotificationDelivery() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public boolean isRead() {
        return read;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
