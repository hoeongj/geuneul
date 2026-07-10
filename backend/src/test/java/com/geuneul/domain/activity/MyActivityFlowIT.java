package com.geuneul.domain.activity;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.auth.AuthProvider;
import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.auth.User;
import com.geuneul.domain.auth.UserRepository;
import com.geuneul.domain.community.ReactionRepository;
import com.geuneul.domain.community.ReviewCommentRepository;
import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "내 글 관리"(N6) 플로우 IT — 실 PostGIS + Security 필터체인에서 후기·댓글·유용해요를 만든 뒤
 * /me/reviews·/me/comments·/me/reactions가 장소명 조인과 함께 최신순으로 돌아오는지, 401/빈 목록을 검증한다.
 * 네이티브 3-way 조인(reactions×reviews×places 등)·Instant→UTC(TS-016)는 실 Postgres에서만 확증된다.
 * 로컬은 colima skip 가능(TS-009) — CI가 최종 게이트.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=geuneul-activity-it-test-secret-0123456789ab") // gitleaks:allow (테스트 전용 더미)
class MyActivityFlowIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReviewRepository reviewRepository;

    @Autowired
    ReviewCommentRepository reviewCommentRepository;

    @Autowired
    ReactionRepository reactionRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    private Long placeId;
    private String token;

    @BeforeEach
    void setUp() {
        reactionRepository.deleteAll();
        reviewCommentRepository.deleteAll();
        reviewRepository.deleteAll();
        placeRepository.deleteAll();
        userRepository.deleteAll();

        Place place = placeRepository.save(Place.of(
                "상도1동 무더위쉼터", PlaceCategory.COOLING_SHELTER, "서울 동작구 성대로 100",
                GeoUtils.point(37.4986, 126.9531), "test", "act-1"));
        placeId = place.getId();

        User user = userRepository.save(User.create(AuthProvider.KAKAO, "kakao-act-1", "act@test.com", "활동러", null));
        token = jwtService.issue(user);
    }

    @Test
    @DisplayName("후기·댓글·유용해요를 남기면 내 활동 3종에 장소명 조인과 함께 돌아온다")
    void myActivityRoundTrips() throws Exception {
        // 후기 작성
        mvc.perform(post("/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":" + placeId + ",\"rating\":5,\"comment\":\"에어컨 시원해요\"}"))
                .andExpect(status().isCreated());
        long reviewId = reviewRepository.findByUserIdAndPlaceId(currentUserId(), placeId).orElseThrow().getId();

        // 내 후기에 댓글 + 유용해요
        mvc.perform(post("/reviews/" + reviewId + "/comments").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"다시 가도 시원함\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/reactions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"REVIEW\",\"targetId\":" + reviewId + ",\"type\":\"HELPFUL\"}"))
                .andExpect(status().isOk());

        // 내 후기
        mvc.perform(get("/me/reviews").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reviewId").value(reviewId))
                .andExpect(jsonPath("$[0].placeId").value(placeId))
                .andExpect(jsonPath("$[0].placeName").value("상도1동 무더위쉼터"))
                .andExpect(jsonPath("$[0].rating").value(5));

        // 내 댓글
        mvc.perform(get("/me/comments").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reviewId").value(reviewId))
                .andExpect(jsonPath("$[0].placeName").value("상도1동 무더위쉼터"))
                .andExpect(jsonPath("$[0].comment").value("다시 가도 시원함"));

        // 내 유용해요(후기 코멘트 미리보기 포함)
        mvc.perform(get("/me/reactions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reviewId").value(reviewId))
                .andExpect(jsonPath("$[0].placeName").value("상도1동 무더위쉼터"))
                .andExpect(jsonPath("$[0].reviewComment").value("에어컨 시원해요"));
    }

    @Test
    @DisplayName("비로그인은 401 (SecurityConfig 보호)")
    void unauthenticatedIs401() throws Exception {
        mvc.perform(get("/me/reviews")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me/comments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me/reactions")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("활동이 없으면 빈 배열")
    void emptyWhenNoActivity() throws Exception {
        mvc.perform(get("/me/reviews").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private long currentUserId() {
        return userRepository.findAll().get(0).getId();
    }
}
