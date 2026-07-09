package com.geuneul.domain.community;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.auth.AuthProvider;
import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.auth.User;
import com.geuneul.domain.auth.UserRepository;
import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.review.Review;
import com.geuneul.domain.review.ReviewRepository;
import com.geuneul.global.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 후기 커뮤니티(댓글·리액션) 플로우 IT — 실 PostGIS + Security 필터체인에서 인증/멱등/404를 검증(TS-009).
 * 로컬은 colima 이슈로 skip될 수 있고 CI가 최종 게이트.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=geuneul-community-it-secret-0123456789ab") // gitleaks:allow (테스트 더미)
class CommunityFlowIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReviewCommentRepository commentRepository;

    @Autowired
    ReactionRepository reactionRepository;

    @Autowired
    ReviewRepository reviewRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    private Long reviewId;
    private String token;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        reactionRepository.deleteAll();
        reviewRepository.deleteAll();
        placeRepository.deleteAll();
        userRepository.deleteAll();

        Place place = placeRepository.save(Place.of(
                "노들서가", PlaceCategory.STUDY_CAFE, "서울 동작구",
                GeoUtils.point(37.5124, 126.9530), "test", "cm-1"));
        User author = userRepository.save(
                User.create(AuthProvider.KAKAO, "kakao-cm-user", "u@test.com", "댓글러", null));
        token = jwtService.issue(author);
        Review review = reviewRepository.save(
                Review.of(author.getId(), place.getId(), (short) 5, "좋아요", null));
        reviewId = review.getId();
    }

    @Test
    @DisplayName("후기 댓글 작성(로그인) → 목록에 돌아온다")
    void addCommentThenList() throws Exception {
        mvc.perform(post("/reviews/" + reviewId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\" 여기 시원해요! \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").value("여기 시원해요!"))   // strip 정규화
                .andExpect(jsonPath("$.nickname").value("댓글러"));

        mvc.perform(get("/reviews/" + reviewId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].comment").value("여기 시원해요!"));
    }

    @Test
    @DisplayName("비로그인 댓글 작성은 401")
    void commentRequiresAuth() throws Exception {
        mvc.perform(post("/reviews/" + reviewId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"익명 댓글\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("리액션 추가는 멱등(2번 눌러도 count=1), 취소하면 count=0")
    void reactionIsIdempotentAndTogglable() throws Exception {
        String body = "{\"targetType\":\"REVIEW\",\"targetId\":" + reviewId + "}";

        mvc.perform(post("/reactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reacted").value(true))
                .andExpect(jsonPath("$.count").value(1));

        // 같은 유저가 다시 추가 → 멱등(count 그대로 1)
        mvc.perform(post("/reactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        // 취소 → count 0
        mvc.perform(delete("/reactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reacted").value(false))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("없는 대상에 리액션하면 404")
    void reactToMissingTargetIs404() throws Exception {
        mvc.perform(post("/reactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"REVIEW\",\"targetId\":999999}"))
                .andExpect(status().isNotFound());
    }
}
