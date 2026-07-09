package com.geuneul.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    /** 후기의 댓글 목록(작성자 조인) 오래된 순 — 대화 흐름은 시간 오름차순이 자연스럽다. */
    @Query(value = """
            SELECT c.id AS id, c.user_id AS userId, c.comment AS comment,
                   u.nickname AS nickname, u.profile_image AS profileImage, c.created_at AS createdAt
            FROM review_comments c
            JOIN users u ON u.id = c.user_id
            WHERE c.review_id = :reviewId
            ORDER BY c.created_at ASC
            """, nativeQuery = true)
    List<ReviewCommentWithAuthorView> findByReviewIdWithAuthor(@Param("reviewId") long reviewId);
}
