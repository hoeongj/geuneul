package com.geuneul.domain.review.dto;

import com.geuneul.domain.review.Review;
import com.geuneul.domain.review.ReviewWithAuthorView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/** 후기 응답. survival_score와 분리된 영구 평판 — rating/comment/photos + 작성자 표시정보. */
@Schema(description = "후기 (영구 평판)")
public record ReviewResponse(
        @Schema(description = "후기 ID", example = "1") Long id,
        @Schema(description = "장소 ID", example = "1") Long placeId,
        @Schema(description = "작성자 닉네임", example = "그늘사랑") String authorNickname,
        @Schema(description = "작성자 프로필 이미지", nullable = true) String authorProfileImage,
        @Schema(description = "별점(1~5)", example = "5") int rating,
        @Schema(description = "코멘트", nullable = true) String comment,
        @Schema(description = "사진 URL 목록") List<String> photos,
        @Schema(description = "작성 시각") OffsetDateTime createdAt,
        @Schema(description = "수정 시각 — 재작성 시 갱신") OffsetDateTime updatedAt
) {

    public static ReviewResponse of(Review r, String nickname, String profileImage, List<String> photos) {
        return new ReviewResponse(r.getId(), r.getPlaceId(), nickname, profileImage, r.getRating(),
                r.getComment(), photos, r.getCreatedAt(), r.getUpdatedAt());
    }

    public static ReviewResponse of(ReviewWithAuthorView v, List<String> photos) {
        return new ReviewResponse(v.getId(), v.getPlaceId(), v.getNickname(), v.getProfileImage(),
                v.getRating(), v.getComment(), photos, v.getCreatedAt(), v.getUpdatedAt());
    }
}
