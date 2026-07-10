package com.geuneul.domain.bookmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 저장/해제 결과 — 리액션 토글과 동일한 최소 상태 셰이프. */
@Schema(description = "관심 장소 저장 상태")
public record BookmarkToggleResponse(
        @Schema(description = "장소 ID", example = "185") long placeId,
        @Schema(description = "현재 저장 상태", example = "true") boolean bookmarked
) {
    public static BookmarkToggleResponse of(long placeId, boolean bookmarked) {
        return new BookmarkToggleResponse(placeId, bookmarked);
    }
}
