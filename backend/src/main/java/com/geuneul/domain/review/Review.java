package com.geuneul.domain.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 영구 후기(평판) — survival_score(휘발성 상태)와 완전히 분리된 장소 평판 콘텐츠
 * (CLAUDE.md §1 UGC 2단 구조, §5, §8 ERD reviews). 로그인 필요, 익명 불가.
 *
 * 정책: <b>장소당 1건 upsert</b> — 같은 유저가 같은 장소에 다시 작성하면 기존 후기를 갱신한다
 * (구글맵의 "계정당 업체 1건" 관행과 동일, WORKLOG 2026-07-09 근거). DB 유니크 제약은 두지 않았다
 * (reviews 테이블은 이미 라이브 스키마 V2 — 신규 마이그레이션 없이 서비스 레이어에서 upsert로 강제,
 * ReviewService.create 참고. 동시 이중 제출 레이스는 이론상 가능하나 로그인 필요 + 저빈도 UGC라 MVP 허용 리스크).
 *
 * place/user는 연관 로딩이 필요 없어 FK id 컬럼만 매핑한다(Report와 동일 경량 패턴 — 검증은 서비스에서).
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    // SMALLINT(V2) ↔ Java short. int로 매핑하면 ddl-auto=validate가 컬럼 타입 불일치로 부팅 시 실패한다.
    @Column(nullable = false)
    private short rating;

    @Column(length = 1000)
    private String comment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "photos_json")
    private String photosJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** 모더레이션 숨김(V12): 신고 RESOLVED 시 true. 공개 후기 목록에서 제외. */
    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    protected Review() {
    }

    public static Review of(long userId, long placeId, short rating, String comment, String photosJson) {
        Review r = new Review();
        r.userId = userId;
        r.placeId = placeId;
        r.rating = rating;
        r.comment = comment;
        r.photosJson = photosJson;
        return r;
    }

    /** 재작성(upsert) — rating/comment/photos만 갱신. 작성자·작성 시각(created_at)은 불변. */
    public void updateContent(short rating, String comment, String photosJson) {
        this.rating = rating;
        this.comment = comment;
        this.photosJson = photosJson;
    }

    public boolean isHidden() {
        return hidden;
    }

    /** 모더레이션 숨김 처리(신고 RESOLVED). */
    public void hide() {
        this.hidden = true;
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

    /** DTO/서비스 편의를 위해 int로 노출(값 범위 1~5라 손실 없음). */
    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public String getPhotosJson() {
        return photosJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
