package com.geuneul.domain.follow;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.auth.AuthProvider;
import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.auth.User;
import com.geuneul.domain.auth.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 커먼스 세이프 팔로우(N7) 플로우 IT — 실 PostGIS + Security 필터체인에서 공개 프로필→팔로우→내 팔로잉→언팔로우
 * 를 엔드투엔드로 검증한다. UNIQUE 멱등·자기팔로우 CHECK·프로필 후기 조인·"팔로워 목록 없음(카운트만)"을 실 SQL로 확인.
 * 로컬 colima skip 가능(TS-009) — CI가 최종 게이트.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=geuneul-follow-it-test-secret-0123456789ab") // gitleaks:allow (테스트 전용 더미)
class FollowFlowIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    FollowRepository followRepository;

    @Autowired
    ReviewRepository reviewRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    private Long authorId;   // 팔로우 당하는 작성자 B
    private String viewerToken; // 팔로우 하는 뷰어 A
    private Long viewerId;

    @BeforeEach
    void setUp() {
        followRepository.deleteAll();
        reviewRepository.deleteAll();
        placeRepository.deleteAll();
        userRepository.deleteAll();

        Place place = placeRepository.save(Place.of(
                "상도1동 무더위쉼터", PlaceCategory.COOLING_SHELTER, "서울 동작구 성대로 100",
                GeoUtils.point(37.4986, 126.9531), "test", "fo-1"));

        User author = userRepository.save(User.create(AuthProvider.KAKAO, "kakao-fo-author", "b@test.com", "좋은작성자", null));
        authorId = author.getId();
        String authorToken = jwtService.issue(author);

        User viewer = userRepository.save(User.create(AuthProvider.GOOGLE, "google-fo-viewer", "a@test.com", "구독자", null));
        viewerId = viewer.getId();
        viewerToken = jwtService.issue(viewer);

        // 작성자 B가 후기 하나 남긴다(프로필 후기 목록 확인용).
        try {
            mvc.perform(post("/reviews").header("Authorization", "Bearer " + authorToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"placeId\":" + place.getId() + ",\"rating\":5,\"comment\":\"여기 시원해요\"}"))
                    .andExpect(status().isCreated());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("공개 프로필: 비로그인도 닉네임·팔로워수·후기목록을 보고, following=false")
    void publicProfileVisibleToAnyone() throws Exception {
        mvc.perform(get("/users/" + authorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(authorId))
                .andExpect(jsonPath("$.nickname").value("좋은작성자"))
                .andExpect(jsonPath("$.followerCount").value(0))
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.reviews.length()").value(1))
                .andExpect(jsonPath("$.reviews[0].placeName").value("상도1동 무더위쉼터"));
    }

    @Test
    @DisplayName("팔로우 → 프로필 following=true·카운트 1 → 내 팔로잉에 등장 → 언팔로우로 원복(멱등)")
    void followThenListThenUnfollow() throws Exception {
        // 팔로우
        mvc.perform(post("/users/" + authorId + "/follow").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));

        // 다시 팔로우해도 멱등(카운트 그대로)
        mvc.perform(post("/users/" + authorId + "/follow").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followerCount").value(1));

        // 뷰어가 보는 프로필엔 following=true
        mvc.perform(get("/users/" + authorId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));

        // 내 팔로잉 목록("나만" 봄)에 작성자 등장
        mvc.perform(get("/me/following").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(authorId))
                .andExpect(jsonPath("$[0].nickname").value("좋은작성자"));

        // 언팔로우 → 원복
        mvc.perform(delete("/users/" + authorId + "/follow").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followerCount").value(0));

        mvc.perform(get("/me/following").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("자기 자신 팔로우는 400")
    void selfFollowIs400() throws Exception {
        mvc.perform(post("/users/" + viewerId + "/follow").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 유저 팔로우는 404")
    void followUnknownIs404() throws Exception {
        mvc.perform(post("/users/999999/follow").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("비로그인 팔로우/내 팔로잉은 401 (SecurityConfig 보호)")
    void unauthenticatedIs401() throws Exception {
        mvc.perform(post("/users/" + authorId + "/follow")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me/following")).andExpect(status().isUnauthorized());
    }
}
