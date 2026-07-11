package com.geuneul.domain.search;

import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.report.ExternalApiRateLimiter;
import com.geuneul.domain.report.ProxyClientResolver;
import com.geuneul.domain.search.dto.PlaceSearchResult;
import com.geuneul.global.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지정 장소 검색 컨트롤러 단위 테스트(N5, MockMvc) — 공백 검색어 400·정상 위임·위치 바이어스 전달을 검증.
 * 카카오 실 파싱은 KakaoKeywordClientTest, 실호출 계약은 TS-026이 담당한다. /places/search는 permitAll.
 */
@WebMvcTest(KeywordSearchController.class)
@Import({SecurityConfig.class, ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class KeywordSearchControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    KakaoKeywordClient keywordClient;

    @MockitoBean
    JwtService jwtService;

    @MockitoBean
    ExternalApiRateLimiter rateLimiter;

    @MockitoBean
    ProxyClientResolver clientResolver;

    @BeforeEach
    void allowByDefault() {
        given(clientResolver.resolve(org.mockito.ArgumentMatchers.any())).willReturn("x:127.0.0.1");
        given(rateLimiter.tryAcquire(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt())).willReturn(true);
    }

    @Test
    @DisplayName("정상 검색: 200 + 결과 매핑, 클라이언트에 strip된 query 위임")
    void searchOk() throws Exception {
        given(keywordClient.search(eq("강남역"), isNull(), isNull(), eq(10)))
                .willReturn(List.of(new PlaceSearchResult("강남역 2호선", "서울 강남구 역삼동 858",
                        "서울 강남구 강남대로 지하 396", 37.498, 127.028, "지하철역")));

        mvc.perform(get("/places/search").param("query", "  강남역  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("강남역 2호선"))
                .andExpect(jsonPath("$[0].lat").value(37.498))
                .andExpect(jsonPath("$[0].lng").value(127.028));

        then(keywordClient).should().search(eq("강남역"), isNull(), isNull(), eq(10));
    }

    @Test
    @DisplayName("공백 검색어는 400 — 카카오를 호출하지 않는다")
    void blankQueryIs400() throws Exception {
        mvc.perform(get("/places/search").param("query", "   "))
                .andExpect(status().isBadRequest());

        then(keywordClient).should(never()).search(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("레이트리밋 초과면 429 — 카카오를 호출하지 않는다")
    void rateLimitedIs429() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .willReturn(false);

        mvc.perform(get("/places/search").param("query", "강남역"))
                .andExpect(status().isTooManyRequests());

        then(keywordClient).should(never()).search(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("lat/lng를 주면 위치 바이어스로 그대로 전달된다")
    void locationBiasPassedThrough() throws Exception {
        given(keywordClient.search(eq("성균관대"), eq(37.5), eq(127.0), eq(10)))
                .willReturn(List.of());

        mvc.perform(get("/places/search")
                        .param("query", "성균관대")
                        .param("lat", "37.5")
                        .param("lng", "127.0"))
                .andExpect(status().isOk());

        then(keywordClient).should().search(eq("성균관대"), eq(37.5), eq(127.0), eq(10));
    }
}
