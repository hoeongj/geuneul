package com.geuneul.domain.review;

import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.auth.Role;
import com.geuneul.domain.review.dto.ReviewListResponse;
import com.geuneul.domain.review.dto.ReviewResponse;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨트롤러 검증·인증 분기 단위 테스트(MockMvc, DB 불필요) — 실 upsert·조인 SQL은 ReviewFlowIT가 검증한다.
 * SecurityConfig를 명시 Import해 POST /reviews의 인증 요구(비로그인 401)를 실제로 태운다
 * (AuthController에는 이 패턴의 선례가 없어 이 테스트가 처음 확립한다). Boot 4에서 @WebMvcTest 슬라이스는
 * 시큐리티 오토컨피그(HttpSecurity 빈 등)를 기본으로 안 끌어오므로 ServletWebSecurityAutoConfiguration
 * (+SecurityFilterAutoConfiguration)을 함께 Import해야 필터체인이 실제로 MockMvc에 걸린다.
 */
@WebMvcTest(ReviewController.class)
@Import({SecurityConfig.class, ServletWebSecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class ReviewControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReviewService reviewService;

    @MockitoBean
    JwtService jwtService;

    private static ReviewResponse sample() {
        return new ReviewResponse(1L, 1L, "그늘러버", null, 5, "좋아요",
                List.of(), OffsetDateTime.now(), OffsetDateTime.now());
    }

    private void stubValidToken() {
        given(jwtService.parse("valid-token")).willReturn(new JwtService.AuthPrincipal(10L, Role.USER));
    }

    @Test
    @DisplayName("로그인 후 정상 요청은 201 Created + 본문을 돌려준다")
    void createReturns201() throws Exception {
        stubValidToken();
        given(reviewService.create(eq(10L), any())).willReturn(sample());

        mvc.perform(post("/reviews")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":1,\"rating\":5,\"comment\":\"좋아요\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.authorNickname").value("그늘러버"));
    }

    @Test
    @DisplayName("Authorization 헤더 없이 POST하면 401 — 서비스까지 내려가지 않는다")
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":1,\"rating\":5}"))
                .andExpect(status().isUnauthorized());

        then(reviewService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("무효한 토큰(파싱 실패)이면 401")
    void invalidTokenIsRejected() throws Exception {
        given(jwtService.parse("bad-token")).willThrow(new io.jsonwebtoken.security.SignatureException("bad"));

        mvc.perform(post("/reviews")
                        .header("Authorization", "Bearer bad-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":1,\"rating\":5}"))
                .andExpect(status().isUnauthorized());

        then(reviewService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("rating 누락이면 400")
    void missingRatingIs400() throws Exception {
        stubValidToken();
        mvc.perform(post("/reviews")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rating이 범위(1~5) 밖이면 400")
    void ratingOutOfRangeIs400() throws Exception {
        stubValidToken();
        mvc.perform(post("/reviews")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":1,\"rating\":6}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("코멘트 1000자 초과면 400")
    void tooLongCommentIs400() throws Exception {
        stubValidToken();
        String longComment = "가".repeat(1001);
        mvc.perform(post("/reviews")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":1,\"rating\":5,\"comment\":\"" + longComment + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("사진 11장 초과면 400")
    void tooManyPhotosIs400() throws Exception {
        stubValidToken();
        String photos = "[" + "\"https://img/1.jpg\",".repeat(10) + "\"https://img/11.jpg\"]";
        mvc.perform(post("/reviews")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":1,\"rating\":5,\"photos\":" + photos + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("장소별 후기 목록 GET은 로그인 없이 200")
    void listIsPublicAndOk() throws Exception {
        given(reviewService.listByPlace(eq(1L), anyInt(), anyInt()))
                .willReturn(new ReviewListResponse(List.of(sample()), 0, 20, 1, false));

        mvc.perform(get("/places/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[0].rating").value(5))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("size는 최대 50으로 클램프된다")
    void sizeIsClampedToMax() throws Exception {
        given(reviewService.listByPlace(anyLong(), anyInt(), anyInt()))
                .willReturn(new ReviewListResponse(List.of(), 0, 50, 0, false));

        mvc.perform(get("/places/1/reviews").param("size", "500"))
                .andExpect(status().isOk());

        then(reviewService).should().listByPlace(1L, 0, 50);
    }
}
