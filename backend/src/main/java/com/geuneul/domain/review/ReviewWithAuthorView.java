package com.geuneul.domain.review;

import java.time.OffsetDateTime;

/**
 * 장소 후기 목록 투영 — reviews × users 조인(작성자 닉네임/프로필 이미지).
 * {@code GET /places/{id}/reviews} 전용(네이티브 쿼리, ReviewRepository). 인터페이스 프로젝션이라
 * Hibernate 엔티티 스키마 검증과 무관 — 컬럼 별칭↔getter 이름만 맞으면 된다.
 */
public interface ReviewWithAuthorView {

    Long getId();

    Long getPlaceId();

    Integer getRating();

    String getComment();

    String getPhotosJson();

    String getNickname();

    String getProfileImage();

    OffsetDateTime getCreatedAt();

    OffsetDateTime getUpdatedAt();
}
