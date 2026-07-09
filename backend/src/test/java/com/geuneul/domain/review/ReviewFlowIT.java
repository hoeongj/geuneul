package com.geuneul.domain.review;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.auth.AuthProvider;
import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.auth.User;
import com.geuneul.domain.auth.UserRepository;
import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.global.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 후기 플로우 IT — 실 PostGIS + Spring Security 필터체인에서 작성→조회→upsert→401/404를
 * 엔드투엔드로 검증한다. jwt.secret은 프로덕션 기본(빈값)이라 TestPropertySource로 테스트 전용 값을 주입.
 * 로컬은 colima Docker API 협상 이슈로 skip될 수 있다(TS-009) — CI(표준 Docker)가 최종 게이트.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=geuneul-review-it-test-secret-0123456789ab") // gitleaks:allow (테스트 전용 더미)
class ReviewFlowIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReviewRepository reviewRepository;

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
        reviewRepository.deleteAll();
        placeRepository.deleteAll();
        userRepository.deleteAll();
        Place savedPlace = placeRepository.save(Place.of(
                "상도1동 무더위쉼터", PlaceCategory.COOLING_SHELTER, "서울 동작구 성대로 100",
                GeoUtils.point(37.4986, 126.9531), "test", "rv-1"));
        placeId = savedPlace.getId();

        User savedUser = userRepository.save(User.create(AuthProvider.KAKAO, "kakao-rv-1", "rv@test.com", "테스터", null));
        token = jwtService.issue(savedUser);
    }

    @Test
    @DisplayName("후기 작성 → 목록 조회로 돌아온다(작성자 닉네임 조인 포함)")
    void createThenList() throws Exception {
        mvc.perform(post("/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":" + placeId + ",\"rating\":5,\"comment\":\" 시원하고 좋아요 \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("시원하고 좋아요")) // 공백 정규화
                .andExpect(jsonPath("$.authorNickname").value("테스터"));

        mvc.perform(get("/places/" + placeId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.reviews[0].rating").value(5));

        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 유저가 같은 장소에 재작성하면 upsert — 행 수가 늘지 않는다")
    void rewriteUpsertsInPlace() throws Exception {
        mvc.perform(post("/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":" + placeId + ",\"rating\":3,\"comment\":\"그저 그래요\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":" + placeId + ",\"rating\":5,\"comment\":\"다시 가보니 최고\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("다시 가보니 최고"));

        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Authorization 헤더 없이 후기를 작성하면 401")
    void unauthenticatedPostIs401() throws Exception {
        mvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":" + placeId + ",\"rating\":5}"))
                .andExpect(status().isUnauthorized());

        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    @DisplayName("없는 장소에 후기를 작성하면 404 — 유령 장소에 쌓이지 않는다")
    void unknownPlaceIs404() throws Exception {
        mvc.perform(post("/reviews").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":999999,\"rating\":5}"))
                .andExpect(status().isNotFound());

        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    @DisplayName("후기 목록 조회는 로그인 없이도 200, 후기가 없으면 빈 배열")
    void listIsPublicEvenWhenEmpty() throws Exception {
        mvc.perform(get("/places/" + placeId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
