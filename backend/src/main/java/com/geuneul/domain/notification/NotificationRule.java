package com.geuneul.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 알림 규칙(B1, ADR-0018) — 유저가 설정한 알림 조건. 로그인 유저 개인화(살).
 * type별로 쓰는 컬럼이 다르다(SURGE_NEARBY=center+radius, BOOKMARK_SURGE=없음, HEAT_ESCAPE=center) — 검증은 서비스.
 */
@Entity
@Table(name = "notification_rules")
public class NotificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private NotificationRuleType type;

    @Column(name = "center_lat")
    private Double centerLat;

    @Column(name = "center_lng")
    private Double centerLng;

    @Column(name = "radius_m")
    private Integer radiusM;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected NotificationRule() {
    }

    public static NotificationRule of(long userId, NotificationRuleType type,
                                      Double centerLat, Double centerLng, Integer radiusM) {
        NotificationRule r = new NotificationRule();
        r.userId = userId;
        r.type = type;
        r.centerLat = centerLat;
        r.centerLng = centerLng;
        r.radiusM = radiusM;
        r.active = true;
        return r;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public NotificationRuleType getType() {
        return type;
    }

    public Double getCenterLat() {
        return centerLat;
    }

    public Double getCenterLng() {
        return centerLng;
    }

    public Integer getRadiusM() {
        return radiusM;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
