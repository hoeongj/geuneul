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
 * 카카오 로그인 (docs: developers.kakao.com/docs/latest/ko/kakaologin/rest-api).
 * ① 토큰 교환: POST https://kauth.kakao.com/oauth/token (form). client_id=REST API 키.
 * ② 사용자: GET https://kapi.kakao.com/v2/user/me (Bearer).
 *
 * - client_id는 지오코딩과 동일한 앱 REST API 키(KAKAO_REST_API_KEY)를 재사용한다(같은 카카오 앱).
 * - client_secret은 선택(콘솔 [보안]에서 "사용함"일 때만 필요) — 비어 있으면 파라미터를 생략한다.
 * - 이메일 동의(비즈 인증)가 없으면 kakao_account.email이 없으므로 id(회원번호)로만 식별한다.
 * - 응답은 타입 있는 record로 역직렬화(Boot 4/Jackson 3 — TS-004).
 */
@Component
public class KakaoOAuthClient implements OAuthClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoOAuthClient.class);
    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USERINFO_URI = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    @Autowired
    public KakaoOAuthClient(@Value("${kakao.rest-api-key:}") String clientId,
                            @Value("${kakao.oauth.client-secret:}") String clientSecret) {
        this(clientId, clientSecret, RestClient.builder());
    }

    KakaoOAuthClient(String clientId, String clientSecret, RestClient.Builder builder) {
        this.clientId = clientId == null ? "" : clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
        this.restClient = builder.build();
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public OAuthUserInfo exchange(String code, String redirectUri) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", clientId);
            form.add("redirect_uri", redirectUri);
            form.add("code", code);
            if (!clientSecret.isBlank()) {
                form.add("client_secret", clientSecret);
            }

            TokenResponse token = restClient.post()
                    .uri(TOKEN_URI)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (token == null || token.access_token() == null) {
                throw new ResponseStatusException(UNAUTHORIZED, "카카오 토큰 교환 실패");
            }

            KakaoUser user = restClient.get()
                    .uri(USERINFO_URI)
                    .header("Authorization", "Bearer " + token.access_token())
                    .retrieve()
                    .body(KakaoUser.class);
            if (user == null || user.id() == null) {
                throw new ResponseStatusException(UNAUTHORIZED, "카카오 사용자 조회 실패");
            }

            return new OAuthUserInfo(
                    String.valueOf(user.id()),
                    user.email(),
                    user.nickname(),
                    user.profileImage());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[auth:kakao] 교환 실패: {}", e.getMessage());
            throw new ResponseStatusException(UNAUTHORIZED, "카카오 로그인 실패");
        }
    }

    record TokenResponse(String access_token, String token_type, String refresh_token) {
    }

    /** 카카오 /v2/user/me 응답(필요 필드만). id=회원번호. */
    record KakaoUser(Long id, KakaoAccount kakao_account, Properties properties) {

        String email() {
            return kakao_account == null ? null : kakao_account.email();
        }

        String nickname() {
            String fromAccount = kakao_account == null || kakao_account.profile() == null
                    ? null : kakao_account.profile().nickname();
            if (fromAccount != null && !fromAccount.isBlank()) {
                return fromAccount;
            }
            return properties == null ? null : properties.nickname();
        }

        String profileImage() {
            String fromAccount = kakao_account == null || kakao_account.profile() == null
                    ? null : kakao_account.profile().profile_image_url();
            if (fromAccount != null && !fromAccount.isBlank()) {
                return fromAccount;
            }
            return properties == null ? null : properties.profile_image();
        }

        record KakaoAccount(String email, Profile profile) {
        }

        record Profile(String nickname, String profile_image_url) {
        }

        record Properties(String nickname, String profile_image) {
        }
    }
}
