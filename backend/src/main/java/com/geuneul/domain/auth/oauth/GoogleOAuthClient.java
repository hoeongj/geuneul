package com.geuneul.domain.auth.oauth;

import com.geuneul.domain.auth.AuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 구글 로그인 (docs: developers.google.com/identity/protocols/oauth2/web-server).
 * ① 토큰 교환: POST https://oauth2.googleapis.com/token (form).
 * ② 사용자: GET https://openidconnect.googleapis.com/v1/userinfo (Bearer). sub=고유 식별자.
 *
 * - scope는 openid/email/profile(비민감) — 검증 없이 이메일·이름·사진을 받는다.
 * - 응답은 타입 있는 record로 역직렬화(Boot 4/Jackson 3 — TS-004).
 */
@Component
public class GoogleOAuthClient implements OAuthClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClient.class);
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URI = "https://openidconnect.googleapis.com/v1/userinfo";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    @Autowired
    public GoogleOAuthClient(@Value("${google.oauth.client-id:}") String clientId,
                             @Value("${google.oauth.client-secret:}") String clientSecret) {
        this(clientId, clientSecret, RestClient.builder());
    }

    GoogleOAuthClient(String clientId, String clientSecret, RestClient.Builder builder) {
        this.clientId = clientId == null ? "" : clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
        this.restClient = builder.build();
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public OAuthUserInfo exchange(String code, String redirectUri) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("redirect_uri", redirectUri);
            form.add("code", code);

            TokenResponse token = restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (token == null || token.access_token() == null) {
                throw new ResponseStatusException(UNAUTHORIZED, "구글 토큰 교환 실패");
            }

            GoogleUser user = restClient.get()
                    .uri(USERINFO_URI)
                    .header("Authorization", "Bearer " + token.access_token())
                    .retrieve()
                    .body(GoogleUser.class);
            if (user == null || user.sub() == null) {
                throw new ResponseStatusException(UNAUTHORIZED, "구글 사용자 조회 실패");
            }

            return new OAuthUserInfo(user.sub(), user.email(), user.name(), user.picture());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[auth:google] 교환 실패: {}", e.getMessage());
            throw new ResponseStatusException(UNAUTHORIZED, "구글 로그인 실패");
        }
    }

    record TokenResponse(String access_token, String token_type, String id_token) {
    }

    /** 구글 OIDC userinfo 응답(필요 필드만). sub=구글 고유 식별자. */
    record GoogleUser(String sub, String email, String name, String picture) {
    }
}
