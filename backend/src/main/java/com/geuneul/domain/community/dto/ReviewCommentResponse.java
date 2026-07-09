package com.geuneul.domain.community.dto;

import com.geuneul.domain.community.ReviewComment;
import com.geuneul.domain.community.ReviewCommentWithAuthorView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Schema(description = "후기 댓글")
public record ReviewCommentResponse(
        @Schema(description = "댓글 ID") Long id,
        @Schema(description = "작성자 ID") Long userId,
        @Schema(description = "작성자 닉네임") String nickname,
        @Schema(description = "작성자 프로필 이미지", nullable = true) String profileImage,
        @Schema(description = "댓글 내용") String comment,
        @Schema(description = "작성 시각") OffsetDateTime createdAt
) {

    public static ReviewCommentResponse of(ReviewCommentWithAuthorView v) {
        // 네이티브 프로젝션은 Instant로 받는다(TS-016) — 여기서 UTC로 부착(앱 전역 UTC).
        return new ReviewCommentResponse(
                v.getId(), v.getUserId(), v.getNickname(), v.getProfileImage(), v.getComment(),
                v.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    /** 작성 직후 응답(작성자 정보는 호출부가 채운다). */
    public static ReviewCommentResponse of(ReviewComment c, String nickname, String profileImage) {
        return new ReviewCommentResponse(
                c.getId(), c.getUserId(), nickname, profileImage, c.getComment(), c.getCreatedAt());
    }
}
