package com.geuneul.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청 — 프론트 BFF가 제공자 콜백에서 받은 인가 코드와, 인가 때 쓴 자기 콜백 URL을 넘긴다.
 * redirectUri는 로컬/프로덕션이 달라 토큰 교환 시 정확히 일치해야 하므로 프론트가 명시한다.
 */
public record LoginRequest(
        @NotBlank(message = "code는 필수입니다") String code,
        @NotBlank(message = "redirectUri는 필수입니다") String redirectUri
) {
}
