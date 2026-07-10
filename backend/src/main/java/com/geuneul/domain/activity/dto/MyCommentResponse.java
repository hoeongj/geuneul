package com.geuneul.domain.activity.dto;

import com.geuneul.domain.activity.MyCommentView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 내가 쓴 댓글 1건(N6). 댓글이 달린 후기의 장소로 이동할 수 있게 reviewId/placeId/placeName 포함. */
@Schema(description = "내 댓글(내 글 관리)")
public record MyCommentResponse(
        @Schema(description = "댓글 ID") Long commentId,
        @Schema(description = "후기 ID") Long reviewId,
        @Schema(description = "장소 ID") Long placeId,
        @Schema(description = "장소명") String placeName,
        @Schema(description = "댓글 내용") String comment,
        @Schema(description = "작성 시각") OffsetDateTime createdAt
) {
    public static MyCommentResponse of(MyCommentView v) {
        return new MyCommentResponse(v.getId(), v.getReviewId(), v.getPlaceId(), v.getPlaceName(),
                v.getComment(), v.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
