package com.geuneul.domain.auth.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 구글 토큰 교환 + OIDC userinfo 파싱 경로 검증(sub=식별자, email/name/picture 매핑).
 */
class GoogleOAuthClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private GoogleOAuthClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleOAuthClient("g-client-id", "g-secret", builder);
    }

    @Test
    @DisplayName("code 교환 → sub·email·name·picture 매핑")
    void exchangesAndMaps() {
        server.expect(requestTo(containsString("oauth2.googleapis.com/token")))
                .andRespond(withSuccess("{\"access_token\":\"g-at\",\"id_token\":\"jwt\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("openidconnect.googleapis.com/v1/userinfo")))
                .andExpect(header("Authorization", "Bearer g-at"))
                .andRespond(withSuccess("""
                        {"sub":"108154","email":"me@gmail.com","name":"홍길동","picture":"http://img/g.png"}
                        """, MediaType.APPLICATION_JSON));

        OAuthUserInfo info = client.exchange("auth-code", "http://localhost:3000/api/auth/google/callback");

        assertThat(info.providerId()).isEqualTo("108154");
        assertThat(info.email()).isEqualTo("me@gmail.com");
        assertThat(info.nickname()).isEqualTo("홍길동");
        assertThat(info.profileImage()).isEqualTo("http://img/g.png");
        server.verify();
    }
}
