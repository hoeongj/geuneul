package com.geuneul.domain.follow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 팔로우(N7, 커먼스 세이프) — follower_id가 followee_id를 팔로우한다. uq_follow(follower, followee) 유니크로
 * 멱등, chk_no_self_follow로 자기 팔로우 금지(Flyway V17). 팔로우는 사적 북마크 + 인기 신호일 뿐 소셜 그래프가
 * 아니다(§0-9) — 팔로워 목록·피드·맞팔 없음.
 */
@Entity
@Table(name = "follows")
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_id", nullable = false)
    private Long followerId;

    @Column(name = "followee_id", nullable = false)
    private Long followeeId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Follow() {
    }

    public static Follow of(long followerId, long followeeId) {
        Follow f = new Follow();
        f.followerId = followerId;
        f.followeeId = followeeId;
        return f;
    }

    public Long getId() {
        return id;
    }

    public Long getFollowerId() {
        return followerId;
    }

    public Long getFolloweeId() {
        return followeeId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
