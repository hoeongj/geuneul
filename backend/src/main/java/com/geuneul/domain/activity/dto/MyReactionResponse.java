package com.geuneul.domain.activity.dto;

import com.geuneul.domain.activity.MyReactionView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 내가 "유용해요" 누른 후기 1건(N6). 그 후기의 장소로 이동할 수 있게 reviewId/placeId/placeName 포함. */
@Schema(description = "내 유용해요(내 글 관리)")
public record MyReactionResponse(
        @Schema(description = "리액션 ID") Long reactionId,
        @Schema(description = "후기 ID") Long reviewId,
        @Schema(description = "장소 ID") Long placeId,
        @Schema(description = "장소명") String placeName,
        @Schema(description = "후기 코멘트(미리보기)", nullable = true) String reviewComment,
        @Schema(description = "누른 시각") OffsetDateTime createdAt
) {
    public static MyReactionResponse of(MyReactionView v) {
        return new MyReactionResponse(v.getId(), v.getReviewId(), v.getPlaceId(), v.getPlaceName(),
                v.getReviewComment(), v.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
