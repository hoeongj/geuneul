package com.geuneul.domain.auth.oauth;

import com.geuneul.domain.auth.AuthProvider;

/**
 * 소셜 로그인 제공자별 클라이언트 — 인가 코드를 액세스 토큰으로 교환하고 사용자 정보를 가져온다.
 * 리다이렉트는 프론트 BFF가 처리하고(등록된 redirect URI가 Vercel), 백엔드는 code만 받아 서버에서 교환한다.
 */
public interface OAuthClient {

    AuthProvider provider();

    /**
     * 인가 코드(code)와 프론트가 실제로 쓴 redirectUri로 토큰을 교환하고 사용자 정보를 조회한다.
     * redirectUri는 로컬/프로덕션이 다르므로 프론트가 자기 콜백 URL을 그대로 넘긴다(토큰 교환 시 일치 필수).
     *
     * @throws org.springframework.web.server.ResponseStatusException 코드 만료·위조 등 교환 실패 시 401
     */
    OAuthUserInfo exchange(String code, String redirectUri);
}
