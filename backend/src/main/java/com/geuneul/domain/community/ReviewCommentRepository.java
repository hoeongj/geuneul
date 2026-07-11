package com.geuneul.domain.community;

import com.geuneul.domain.activity.MyCommentView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    /** 부모 리뷰가 공개 상태인 댓글만 리액션 대상이 될 수 있다. */
    @Query(value = """
            SELECT COUNT(*) > 0
            FROM review_comments c
            JOIN reviews rv ON rv.id = c.review_id
            WHERE c.id = :id
              AND NOT rv.hidden
            """, nativeQuery = true)
    boolean existsVisibleById(@Param("id") long id);

    /** 후기의 댓글 목록(작성자 조인) 오래된 순 — 대화 흐름은 시간 오름차순이 자연스럽다. */
    @Query(value = """
            SELECT c.id AS id, c.user_id AS userId, c.comment AS comment,
                   u.nickname AS nickname, u.profile_image AS profileImage, c.created_at AS createdAt
            FROM review_comments c
            JOIN users u ON u.id = c.user_id
            JOIN reviews rv ON rv.id = c.review_id
            WHERE c.review_id = :reviewId
              AND NOT rv.hidden
            ORDER BY c.created_at ASC
            """, nativeQuery = true)
    List<ReviewCommentWithAuthorView> findByReviewIdWithAuthor(@Param("reviewId") long reviewId);

    /** 내가 쓴 댓글 목록(N6) — review_comments × reviews × places 조인, 최신순. 댓글의 후기가 달린 장소로 이동. */
    @Query(value = """
            SELECT c.id AS id, c.review_id AS reviewId, rv.place_id AS placeId, p.name AS placeName,
                   c.comment AS comment, c.created_at AS createdAt
            FROM review_comments c
            JOIN reviews rv ON rv.id = c.review_id
            JOIN places p ON p.id = rv.place_id
            WHERE c.user_id = :userId
              AND NOT rv.hidden
              AND p.deleted_at IS NULL
            ORDER BY c.created_at DESC
            LIMIT 100
            """, nativeQuery = true)
    List<MyCommentView> findByUserIdWithPlace(@Param("userId") long userId);
}
