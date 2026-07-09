package com.geuneul.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 후기 작성/수정 요청. 로그인 필요 — user_id는 요청 바디로 받지 않고 JWT(AuthPrincipal)에서 취한다
 * (CLAUDE.md 작업 지시). 사진 presign은 아직 없어 URL 배열 스키마만 수용한다.
 */
@Schema(description = "후기 작성/수정 요청 — 로그인 필요, 장소당 1건(재작성 시 갱신)")
public record ReviewCreateRequest(
        @Schema(description = "장소 ID", example = "1")
        @NotNull(message = "placeId는 필수입니다")
        Long placeId,

        @Schema(description = "별점(1~5)", example = "5")
        @NotNull(message = "rating은 필수입니다")
        @Min(value = 1, message = "rating은 1~5 사이여야 합니다")
        @Max(value = 5, message = "rating은 1~5 사이여야 합니다")
        Integer rating,

        @Schema(description = "코멘트 (선택, 최대 1000자)", example = "에어컨 빵빵하고 콘센트도 많아요")
        @Size(max = 1000, message = "코멘트는 1000자 이하여야 합니다")
        String comment,

        @Schema(description = "사진 URL 목록 (선택, 최대 10장 — presign은 P2 후속, URL 배열 스키마만 수용)")
        @Size(max = 10, message = "사진은 최대 10장입니다")
        List<@Size(max = 512, message = "사진 URL은 512자 이하여야 합니다") String> photos
) {
}
