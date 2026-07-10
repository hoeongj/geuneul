package com.geuneul.domain.follow.dto;

import com.geuneul.domain.activity.dto.MyReviewResponse;
import com.geuneul.domain.auth.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 작성자 공개 프로필(N7) — 닉네임 · 신뢰도(배지) · 팔로워 "수"(공개) · 그 사람 공개 후기 목록.
 * 커먼스라 팔로우 안 해도 누구나 본다(§0-9). {@code following}은 요청 뷰어가 이 작성자를 팔로우 중인지(팔로우
 * 버튼 상태) — 비로그인이면 false. 후기 목록은 N6과 동일 쿼리(ReviewRepository.findByUserIdWithPlace) 재사용.
 * <b>팔로워 "목록"은 어디에도 넣지 않는다</b> — 카운트만 노출(허영 리스트·친구망화 금지).
 */
@Schema(description = "작성자 공개 프로필")
public record UserProfileResponse(
        @Schema(description = "작성자 ID") long id,
        @Schema(description = "닉네임") String nickname,
        @Schema(description = "프로필 이미지", nullable = true) String profileImage,
        @Schema(description = "신뢰도 점수") double trustScore,
        @Schema(description = "팔로워 수(공개 카운트, 목록은 비공개)") long followerCount,
        @Schema(description = "요청 뷰어가 이 작성자를 팔로우 중인지") boolean following,
        @Schema(description = "이 작성자의 공개 후기 목록(최신순)") List<MyReviewResponse> reviews
) {
    public static UserProfileResponse of(User user, long followerCount, boolean following,
                                         List<MyReviewResponse> reviews) {
        return new UserProfileResponse(user.getId(), user.getNickname(), user.getProfileImage(),
                user.getTrustScore(), followerCount, following, reviews);
    }
}
