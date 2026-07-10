package com.geuneul.domain.bookmark;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관심 장소(bookmark) 플로우 IT (A7) — 실 PostGIS + Security 필터체인에서 저장→목록(장소 조인)→해제→멱등→
 * 401/404를 엔드투엔드로 검증한다. 목록의 좌표(ST_Y/ST_X)·시각(Instant→UTC, TS-016) 조인 시맨틱을 실 SQL로 확인.
 * jwt.secret은 프로덕션 기본(빈값)이라 TestPropertySource로 테스트 전용 값 주입. 로컬 colima skip 가능(TS-009).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=geuneul-bookmark-it-test-secret-0123456789ab") // gitleaks:allow (테스트 전용 더미)
class BookmarkFlowIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    BookmarkRepository bookmarkRepository;

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
        bookmarkRepository.deleteAll();
        placeRepository.deleteAll();
        userRepository.deleteAll();
        Place savedPlace = placeRepository.save(Place.of(
                "노들서가", PlaceCategory.STUDY_CAFE, "서울 동작구 노들로 1",
                GeoUtils.point(37.5124, 126.9530), "test", "bm-1"));
        placeId = savedPlace.getId();

        User savedUser = userRepository.save(User.create(AuthProvider.KAKAO, "kakao-bm-1", "bm@test.com", "북마커", null));
        token = jwtService.issue(savedUser);
    }

    private String body(String memo) {
        return "{\"placeId\":" + placeId + (memo == null ? "" : ",\"memo\":\"" + memo + "\"") + "}";
    }

    @Test
    @DisplayName("저장 → 목록에 장소 정보와 memo가 조인돼 돌아온다")
    void saveThenList() throws Exception {
        mvc.perform(post("/bookmarks").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body("충전 자리 많음")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeId").value(placeId))
                .andExpect(jsonPath("$.bookmarked").value(true));

        mvc.perform(get("/me/bookmarks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].placeId").value(placeId))
                .andExpect(jsonPath("$[0].name").value("노들서가"))
                .andExpect(jsonPath("$[0].category").value("STUDY_CAFE"))
                .andExpect(jsonPath("$[0].categoryLabel").value("스터디카페"))
                .andExpect(jsonPath("$[0].lat").value(org.hamcrest.Matchers.closeTo(37.5124, 1e-4)))
                .andExpect(jsonPath("$[0].memo").value("충전 자리 많음"));
    }

    @Test
    @DisplayName("같은 장소 재저장은 멱등(중복 없이 memo 갱신)")
    void resaveIsIdempotentUpsert() throws Exception {
        mvc.perform(post("/bookmarks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body("첫 메모"))).andExpect(status().isCreated());
        mvc.perform(post("/bookmarks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body("바뀐 메모"))).andExpect(status().isCreated());

        mvc.perform(get("/me/bookmarks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))                  // 중복 없음
                .andExpect(jsonPath("$[0].memo").value("바뀐 메모"));         // memo 갱신
    }

    @Test
    @DisplayName("해제하면 목록에서 빠진다(멱등 — 다시 해제해도 200)")
    void removeThenGone() throws Exception {
        mvc.perform(post("/bookmarks").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(null))).andExpect(status().isCreated());

        mvc.perform(delete("/bookmarks/" + placeId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookmarked").value(false));
        // 없는 것을 또 해제해도 멱등 200
        mvc.perform(delete("/bookmarks/" + placeId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/me/bookmarks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("비로그인 저장은 401(SecurityConfig 보호)")
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(post("/bookmarks").contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("없는 장소 저장은 404")
    void missingPlaceIs404() throws Exception {
        mvc.perform(post("/bookmarks").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placeId\":999999}"))
                .andExpect(status().isNotFound());
    }
}
