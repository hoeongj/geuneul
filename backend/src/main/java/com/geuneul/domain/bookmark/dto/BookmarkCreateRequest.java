package com.geuneul.domain.bookmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관심 장소 저장 요청")
public record BookmarkCreateRequest(
        @Schema(description = "저장할 장소 ID", example = "185")
        @NotNull(message = "placeId는 필수입니다")
        Long placeId,

        @Schema(description = "메모(선택, 최대 200자)", example = "노트북 충전 자리 많음", nullable = true)
        @Size(max = 200, message = "메모는 200자 이하입니다")
        String memo
) {
}
