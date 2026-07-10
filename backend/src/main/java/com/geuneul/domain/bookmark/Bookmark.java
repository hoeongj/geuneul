package com.geuneul.domain.bookmark;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 관심 장소(bookmark, A7 · ERD §8) — 로그인 유저가 저장한 장소. survival_score(간판)와 무관한 개인화(살).
 * place/user는 연관 로딩 없이 FK id만 매핑한다(Review·Report와 동일 경량 패턴 — 검증은 서비스).
 * 유저×장소 1건(uq_bookmarks, V14) — memo 갱신은 upsert로 서비스가 처리.
 */
@Entity
@Table(name = "bookmarks")
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(length = 200)
    private String memo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Bookmark() {
    }

    public static Bookmark of(long userId, long placeId, String memo) {
        Bookmark b = new Bookmark();
        b.userId = userId;
        b.placeId = placeId;
        b.memo = memo;
        return b;
    }

    /** 이미 저장된 장소에 다시 저장 시 memo만 갱신(upsert). 작성 시각은 불변. */
    public void updateMemo(String memo) {
        this.memo = memo;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public String getMemo() {
        return memo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
