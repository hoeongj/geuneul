package com.geuneul.domain.review;

import java.time.Instant;

/**
 * 장소 후기 목록 투영 — reviews × users 조인(작성자 닉네임/프로필 이미지).
 * {@code GET /places/{id}/reviews} 전용(네이티브 쿼리, ReviewRepository). 인터페이스 프로젝션이라
 * Hibernate 엔티티 스키마 검증과 무관 — 컬럼 별칭↔getter 이름만 맞으면 된다.
 *
 * <p>created_at/updated_at은 {@link Instant}로 받는다 — 네이티브 쿼리 결과에서 PostgreSQL
 * {@code TIMESTAMPTZ}는 JDBC가 {@code Instant}로 반환하는데, Spring Data의 인터페이스 프로젝션 팩토리는
 * Instant→OffsetDateTime을 자동 변환하지 못해({@code UnsupportedOperationException: Cannot project
 * java.time.Instant to java.time.OffsetDateTime}) getter를 OffsetDateTime으로 선언하면 CI(실 Postgres)
 * 에서만 500으로 터진다(TS-016). 변환은 ReviewResponse.of에서 UTC로 명시(애플리케이션 전역이 UTC — 프로덕션
 * 세 클록이 모두 UTC라는 Report 엔티티의 기존 가정과 동일).
 */
public interface ReviewWithAuthorView {

    Long getId();

    Long getPlaceId();

    Integer getRating();

    String getComment();

    String getPhotosJson();

    String getNickname();

    String getProfileImage();

    Instant getCreatedAt();

    Instant getUpdatedAt();
}
