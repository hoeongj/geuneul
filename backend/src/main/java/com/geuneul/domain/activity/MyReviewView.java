package com.geuneul.domain.activity;

import java.time.Instant;

/**
 * 내 후기 투영(N6) — reviews × places 조인(장소명 포함해 마이페이지에서 원문 장소로 이동).
 * 네이티브 쿼리 결과의 TIMESTAMPTZ는 {@link Instant}로 받는다(TS-016 — OffsetDateTime 프로젝션은 CI에서만 500).
 */
public interface MyReviewView {

    Long getId();

    Long getPlaceId();

    String getPlaceName();

    Integer getRating();

    String getComment();

    Instant getCreatedAt();
}
