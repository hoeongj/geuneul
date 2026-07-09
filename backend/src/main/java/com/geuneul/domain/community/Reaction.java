package com.geuneul.domain.community;

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
 * 리액션(CLAUDE.md §8 2차·살) — 후기/제보/댓글에 대한 "유용했어요" 등. target은 다형(target_type+target_id).
 * uq_reaction(target_type, target_id, user_id, type) 유니크로 중복 리액션 방지(토글 멱등). Flyway V11.
 */
@Entity
@Table(name = "reactions")
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private ReactionTarget targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReactionType type;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Reaction() {
    }

    public static Reaction of(ReactionTarget targetType, long targetId, long userId, ReactionType type) {
        Reaction r = new Reaction();
        r.targetType = targetType;
        r.targetId = targetId;
        r.userId = userId;
        r.type = type;
        return r;
    }

    public Long getId() {
        return id;
    }
}
