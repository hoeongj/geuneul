package com.geuneul.domain.follow.dto;

import com.geuneul.domain.follow.FollowingView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 내 팔로잉 목록 1건(N7, "나만" 봄) — 팔로우한 작성자 재방문용. Instant→UTC 부착(TS-016). */
@Schema(description = "내 팔로잉(작성자)")
public record FollowingResponse(
        @Schema(description = "작성자 ID") Long userId,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "프로필 이미지", nullable = true) String profileImage,
        @Schema(description = "신뢰도 점수") double trustScore,
        @Schema(description = "팔로우한 시각") OffsetDateTime followedAt
) {
    public static FollowingResponse of(FollowingView v) {
        return new FollowingResponse(v.getUserId(), v.getNickname(), v.getProfileImage(),
                v.getTrustScore() == null ? 0 : v.getTrustScore(),
                v.getFollowedAt().atOffset(ZoneOffset.UTC));
    }
}
