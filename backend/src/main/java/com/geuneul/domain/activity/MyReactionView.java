package com.geuneul.domain.activity;

import java.time.Instant;

/**
 * 내 "유용해요" 투영(N6) — reactions × reviews × places 조인(내가 유용해요 누른 후기 + 그 장소).
 * 후기 리액션(target_type='REVIEW')만 대상. TIMESTAMPTZ는 {@link Instant}로 받는다(TS-016).
 */
public interface MyReactionView {

    Long getId();

    Long getReviewId();

    Long getPlaceId();

    String getPlaceName();

    String getReviewComment();

    Instant getCreatedAt();
}
