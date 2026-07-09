package com.geuneul.domain.flag;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.auth.AuthProvider;
import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.auth.Role;
import com.geuneul.domain.auth.User;
import com.geuneul.domain.auth.UserRepository;
import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.report.Report;
import com.geuneul.domain.report.ReportRepository;
import com.geuneul.domain.report.ReportType;
import com.geuneul.global.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 모더레이션 숨김 IT (V12) — 신고 RESOLVED 시 대상 제보가 공개 조회에서 사라지고(숨김), DISMISSED면
 * 그대로 남는지, 상태별 이력 조회가 되는지 검증. 실 PostGIS + Security(TS-009, CI 게이트).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=geuneul-moderation-it-secret-0123456789ab") // gitleaks:allow (테스트 더미)
class ModerationHideIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    FlagRepository flagRepository;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JwtService jwtService;

    private Long placeId;
    private Long reportId;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        flagRepository.deleteAll();
        reportRepository.deleteAll();
        placeRepository.deleteAll();
        userRepository.deleteAll();

        Place place = placeRepository.save(Place.of(
                "상도1동 무더위쉼터", PlaceCategory.COOLING_SHELTER, "서울 동작구",
                GeoUtils.point(37.4986, 126.9531), "test", "mod-1"));
        placeId = place.getId();
        Report report = reportRepository.save(Report.anonymous(
                placeId, ReportType.COOL, "허위 제보", true, OffsetDateTime.now().plusHours(1)));
        reportId = report.getId();

        User user = userRepository.save(User.create(AuthProvider.KAKAO, "kakao-mod-u", "u@t.com", "신고자", null));
        userToken = jwtService.issue(user);
        User admin = userRepository.save(User.create(AuthProvider.GOOGLE, "google-mod-a", "a@t.com", "관리자", null));
        setRole(admin, Role.ADMIN);
        adminToken = jwtService.issue(admin);
    }

    private long flagReport() throws Exception {
        mvc.perform(post("/flags").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"REPORT\",\"targetId\":" + reportId + ",\"reason\":\"FALSE_INFO\"}"))
                .andExpect(status().isCreated());
        // 각 테스트는 setUp에서 flag를 비우고 정확히 1건만 만든다.
        return flagRepository.findAll().get(0).getId();
    }

    @Test
    @DisplayName("신고 RESOLVED → 제보가 공개 조회에서 사라진다(숨김) + 이력 조회에 잡힌다")
    void resolvedHidesReport() throws Exception {
        long flagId = flagReport();

        // 처리 전: 공개 조회에 보인다
        mvc.perform(get("/places/" + placeId + "/reports"))
                .andExpect(jsonPath("$.length()").value(1));

        // 관리자 RESOLVED
        mvc.perform(post("/admin/flags/" + flagId + "/resolve").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());

        // 처리 후: 숨김 → 공개 조회에서 사라진다
        mvc.perform(get("/places/" + placeId + "/reports"))
                .andExpect(jsonPath("$.length()").value(0));

        // 이력 조회(status=RESOLVED)엔 잡힌다
        mvc.perform(get("/admin/flags?status=RESOLVED").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags.length()").value(1));
    }

    @Test
    @DisplayName("신고 DISMISSED(오신고) → 제보는 그대로 남는다")
    void dismissedKeepsReport() throws Exception {
        long flagId = flagReport();

        mvc.perform(post("/admin/flags/" + flagId + "/resolve").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/places/" + placeId + "/reports"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    private static void setRole(User user, Role role) {
        try {
            Field f = User.class.getDeclaredField("role");
            f.setAccessible(true);
            f.set(user, role);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
