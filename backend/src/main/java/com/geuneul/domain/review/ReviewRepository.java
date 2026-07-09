package com.geuneul.domain.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 같은 유저·같은 장소의 기존 후기 — upsert 판정(정책: 장소당 1건, Review 클래스 주석 참고). */
    Optional<Review> findByUserIdAndPlaceId(long userId, long placeId);

    /**
     * 유저의 총 후기 수(=리뷰한 장소 수, 장소당 1건 upsert 정책) — trust_score 활동량 신호
     * ({@link com.geuneul.domain.auth.TrustScore}). idx_reviews_user(V6) 경로.
     */
    long countByUserId(long userId);

    /**
     * 장소의 후기 목록(작성자 조인) 최신순 페이지네이션. 네이티브 쿼리라 countQuery를 별도로 명시해야
     * Spring Data가 total count를 낼 수 있다(복잡한 JOIN 쿼리에서 COUNT 자동 유도가 불가능하므로).
     */
    @Query(value = """
            SELECT r.id AS id, r.place_id AS placeId, r.rating AS rating, r.comment AS comment,
                   r.photos_json AS photosJson, u.nickname AS nickname, u.profile_image AS profileImage,
                   r.created_at AS createdAt, r.updated_at AS updatedAt
            FROM reviews r
            JOIN users u ON u.id = r.user_id
            WHERE r.place_id = :placeId
            ORDER BY r.created_at DESC
            """,
            countQuery = "SELECT count(*) FROM reviews r WHERE r.place_id = :placeId",
            nativeQuery = true)
    Page<ReviewWithAuthorView> findByPlaceIdWithAuthor(@Param("placeId") long placeId, Pageable pageable);
}
