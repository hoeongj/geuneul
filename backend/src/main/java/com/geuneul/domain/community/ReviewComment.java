package com.geuneul.domain.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 후기 댓글(docs/SPEC.md §8 2차·살) — 후기(review)에 달리는 댓글. 스키마 소유권은 Flyway V11.
 * 로그인 필요(user_id NOT NULL). survival_score(간판)와 무관한 커뮤니티 콘텐츠(§0-9).
 */
@Entity
@Table(name = "review_comments")
public class ReviewComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ReviewComment() {
    }

    public static ReviewComment of(long reviewId, long userId, String comment) {
        ReviewComment c = new ReviewComment();
        c.reviewId = reviewId;
        c.userId = userId;
        c.comment = comment;
        return c;
    }

    public Long getId() {
        return id;
    }

    public Long getReviewId() {
        return reviewId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getComment() {
        return comment;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
