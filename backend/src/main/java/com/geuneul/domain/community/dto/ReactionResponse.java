package com.geuneul.domain.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 리액션 결과 — 현재 유저의 반응 여부(reacted)와 대상의 총 개수(count). */
@Schema(description = "리액션 상태")
public record ReactionResponse(
        @Schema(description = "현재 유저가 이 리액션을 남긴 상태인가", example = "true") boolean reacted,
        @Schema(description = "대상의 해당 리액션 총 개수", example = "7") long count
) {
    public static ReactionResponse of(boolean reacted, long count) {
        return new ReactionResponse(reacted, count);
    }
}
