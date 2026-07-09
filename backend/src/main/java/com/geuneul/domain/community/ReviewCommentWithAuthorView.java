package com.geuneul.domain.community;

import java.time.OffsetDateTime;

/** 후기 댓글 + 작성자(닉네임/프로필) 조인 투영 — 목록 응답용. */
public interface ReviewCommentWithAuthorView {
    Long getId();

    Long getUserId();

    String getComment();

    String getNickname();

    String getProfileImage();

    OffsetDateTime getCreatedAt();
}
