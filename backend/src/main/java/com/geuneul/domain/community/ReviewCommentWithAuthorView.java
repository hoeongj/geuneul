package com.geuneul.domain.community;

import java.time.Instant;

/**
 * 후기 댓글 + 작성자(닉네임/프로필) 조인 투영 — 목록 응답용.
 *
 * <p>created_at은 {@link Instant}로 받는다(TS-016, ReviewWithAuthorView와 동일 함정): 네이티브 쿼리
 * 결과의 PostgreSQL {@code TIMESTAMPTZ}를 JDBC가 Instant로 반환하는데, Spring Data 인터페이스 프로젝션
 * 팩토리는 Instant→OffsetDateTime을 자동 변환하지 못해({@code UnsupportedOperationException: Cannot
 * project java.time.Instant to java.time.OffsetDateTime}) getter를 OffsetDateTime으로 선언하면 실 Postgres
 * (CI)에서만 500으로 터진다. 변환은 ReviewCommentResponse.of에서 UTC로 명시한다(앱 전역 UTC 가정).
 */
public interface ReviewCommentWithAuthorView {
    Long getId();

    Long getUserId();

    String getComment();

    String getNickname();

    String getProfileImage();

    Instant getCreatedAt();
}
