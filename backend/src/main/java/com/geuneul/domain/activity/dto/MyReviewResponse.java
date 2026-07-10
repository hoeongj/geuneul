package com.geuneul.domain.activity.dto;

import com.geuneul.domain.activity.MyReviewView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 내가 쓴 후기 1건(N6, 마이페이지 "내 활동"). 원문 장소로 이동할 수 있게 placeId/placeName 포함. */
@Schema(description = "내 후기(내 글 관리)")
public record MyReviewResponse(
        @Schema(description = "후기 ID") Long reviewId,
        @Schema(description = "장소 ID") Long placeId,
        @Schema(description = "장소명") String placeName,
        @Schema(description = "별점(1~5)") int rating,
        @Schema(description = "코멘트", nullable = true) String comment,
        @Schema(description = "작성 시각") OffsetDateTime createdAt
) {
    public static MyReviewResponse of(MyReviewView v) {
        return new MyReviewResponse(v.getId(), v.getPlaceId(), v.getPlaceName(), v.getRating(),
                v.getComment(), v.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
