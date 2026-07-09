package com.geuneul.domain.auth.oauth;

/**
 * 소셜 제공자에서 받아온 사용자 식별 정보(정규화). providerId는 필수, 나머지는 동의/제공 여부에 따라 null 가능.
 */
public record OAuthUserInfo(
        String providerId,
        String email,
        String nickname,
        String profileImage
) {
}
