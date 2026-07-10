package com.geuneul.domain.activity;

import java.time.Instant;

/**
 * 내 댓글 투영(N6) — review_comments × reviews × places 조인(댓글이 달린 후기의 장소로 이동).
 * TIMESTAMPTZ는 {@link Instant}로 받는다(TS-016).
 */
public interface MyCommentView {

    Long getId();

    Long getReviewId();

    Long getPlaceId();

    String getPlaceName();

    String getComment();

    Instant getCreatedAt();
}
