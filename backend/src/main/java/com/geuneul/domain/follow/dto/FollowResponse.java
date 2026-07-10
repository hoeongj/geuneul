package com.geuneul.domain.follow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 팔로우/언팔로우 결과(N7) — 토글 후 상태 + 갱신된 팔로워 수(버튼·카운트 즉시 반영). */
@Schema(description = "팔로우 토글 결과")
public record FollowResponse(
        @Schema(description = "팔로우 중 여부") boolean following,
        @Schema(description = "갱신된 팔로워 수") long followerCount
) {
}
