package com.geuneul.domain.auth.dto;

import com.geuneul.domain.auth.AuthProvider;
import com.geuneul.domain.auth.Role;
import com.geuneul.domain.auth.User;

/** 사용자 공개 프로필 응답(/me, 로그인 결과). 민감 정보는 노출하지 않는다. */
public record UserResponse(
        long id,
        AuthProvider provider,
        String nickname,
        String email,
        String profileImage,
        double trustScore,
        Role role
) {
    public static UserResponse of(User u) {
        return new UserResponse(u.getId(), u.getProvider(), u.getNickname(), u.getEmail(),
                u.getProfileImage(), u.getTrustScore(), u.getRole());
    }
}
