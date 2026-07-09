package com.geuneul.domain.auth.dto;

import com.geuneul.domain.auth.AuthService.AuthResult;

/** 로그인 응답 — JWT 액세스 토큰 + 사용자 프로필. 프론트는 토큰을 httpOnly 쿠키로 보관(BFF). */
public record AuthResponse(String token, UserResponse user) {

    public static AuthResponse of(AuthResult result) {
        return new AuthResponse(result.token(), UserResponse.of(result.user()));
    }
}
