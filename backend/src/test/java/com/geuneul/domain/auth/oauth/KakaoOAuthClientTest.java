package com.geuneul.domain.auth.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 카카오 토큰 교환 + /v2/user/me 파싱 경로 검증(Boot 4/Jackson 3 record 매핑, TS-004 취지).
 */
class KakaoOAuthClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoOAuthClient("rest-key", "", builder);
    }

    @Test
    @DisplayName("code 교환 → 회원번호(id)·닉네임 매핑, 이메일 없으면 null")
    void exchangesAndMaps() {
        server.expect(requestTo(containsString("kauth.kakao.com/oauth/token")))
                .andRespond(withSuccess("{\"access_token\":\"kakao-at\",\"token_type\":\"bearer\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("kapi.kakao.com/v2/user/me")))
                .andExpect(header("Authorization", "Bearer kakao-at"))
                .andRespond(withSuccess("""
                        {"id":1234567890,
                         "kakao_account":{"profile":{"nickname":"그늘러","profile_image_url":"http://img/k.png"}},
                         "properties":{"nickname":"그늘러"}}
                        """, MediaType.APPLICATION_JSON));

        OAuthUserInfo info = client.exchange("auth-code", "http://localhost:3000/api/auth/kakao/callback");

        assertThat(info.providerId()).isEqualTo("1234567890"); // 숫자 id → 문자열
        assertThat(info.nickname()).isEqualTo("그늘러");
        assertThat(info.profileImage()).isEqualTo("http://img/k.png");
        assertThat(info.email()).isNull();                     // 이메일 동의 없음
        server.verify();
    }

    @Test
    @DisplayName("토큰 교환 실패(4xx)는 401 ResponseStatusException")
    void tokenExchangeFailureIs401() {
        server.expect(requestTo(containsString("kauth.kakao.com/oauth/token")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.exchange("bad-code", "http://localhost:3000/api/auth/kakao/callback"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }
}
