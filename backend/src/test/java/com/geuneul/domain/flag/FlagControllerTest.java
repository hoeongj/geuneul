package com.geuneul.domain.flag;

import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.auth.Role;
import com.geuneul.domain.flag.dto.FlagResponse;
import com.geuneul.global.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨트롤러 검증·인증 분기 단위 테스트(MockMvc, DB 불필요) — 실 저장·중복판정은 FlagFlowIT가 검증한다.
 * SecurityConfig 3종 Import는 ReviewControllerTest가 확립한 패턴 재사용(TS-015 — Boot 4 @WebMvcTest는
 * 시큐리티 오토컨피그를 기본으로 안 끌어온다).
 */
@WebMvcTest(FlagController.class)
@Import({SecurityConfig.class, ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class FlagControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    FlagService flagService;

    @MockitoBean
    JwtService jwtService;

    private static FlagResponse sample() {
        return new FlagResponse(1L, "REPORT", 1L, "FALSE_INFO", "가짜 같아요", "PENDING",
                OffsetDateTime.now(), null);
    }

    private void stubValidToken() {
        given(jwtService.parse("valid-token")).willReturn(new JwtService.AuthPrincipal(10L, Role.USER));
    }

    @Test
    @DisplayName("로그인 후 정상 요청은 201 Created + 본문을 돌려준다")
    void createReturns201() throws Exception {
        stubValidToken();
        given(flagService.create(eq(10L), any())).willReturn(sample());

        mvc.perform(post("/flags")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"REPORT\",\"targetId\":1,\"reason\":\"FALSE_INFO\",\"detail\":\"가짜 같아요\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reason").value("FALSE_INFO"));
    }

    @Test
    @DisplayName("Authorization 헤더 없이 POST하면 401 — 서비스까지 내려가지 않는다")
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(post("/flags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"REPORT\",\"targetId\":1,\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());

        then(flagService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("무효한 토큰이면 401")
    void invalidTokenIsRejected() throws Exception {
        given(jwtService.parse("bad-token")).willThrow(new io.jsonwebtoken.security.SignatureException("bad"));

        mvc.perform(post("/flags")
                        .header("Authorization", "Bearer bad-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"REPORT\",\"targetId\":1,\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());

        then(flagService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("targetType 누락이면 400")
    void missingTargetTypeIs400() throws Exception {
        stubValidToken();
        mvc.perform(post("/flags")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":1,\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("정의되지 않은 reason 값이면 400")
    void unknownReasonIs400() throws Exception {
        stubValidToken();
        mvc.perform(post("/flags")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"REPORT\",\"targetId\":1,\"reason\":\"LAVA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("detail 500자 초과면 400")
    void tooLongDetailIs400() throws Exception {
        stubValidToken();
        String longDetail = "가".repeat(501);
        mvc.perform(post("/flags")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"REPORT\",\"targetId\":1,\"reason\":\"SPAM\",\"detail\":\"" + longDetail + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이미 신고한 대상이면 409를 그대로 전달한다")
    void duplicateFlagReturns409() throws Exception {
        stubValidToken();
        given(flagService.create(eq(10L), any()))
                .willThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "이미 신고한 대상입니다"));

        mvc.perform(post("/flags")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"REPORT\",\"targetId\":1,\"reason\":\"SPAM\"}"))
                .andExpect(status().isConflict());
    }
}
