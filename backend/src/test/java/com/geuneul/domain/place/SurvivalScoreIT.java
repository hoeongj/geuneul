package com.geuneul.domain.place;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.report.Report;
import com.geuneul.domain.report.ReportRepository;
import com.geuneul.domain.report.ReportType;
import com.geuneul.global.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * survival_score 엔드투엔드 IT — 실 PostGIS에서 place_report_signals 뷰(V4) + 조립(SurvivalScore)을
 * API 응답까지 관통 검증한다. 시공간 신호(freshness/comfort/risk)·만료 제외·3색 등급을 실 SQL로 확인.
 * (뷰의 now() 기반 최근성 버킷은 H2가 아니라 실 PostGIS라야 정확 — AbstractIntegrationTest는 postgis 컨테이너.)
 */
@AutoConfigureMockMvc
class SurvivalScoreIT extends AbstractIntegrationTest {

    private static final double LAT = 37.4986;
    private static final double LNG = 126.9531;

    @Autowired
    MockMvc mvc;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    ReportRepository reportRepository;

    private Long placeId;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        placeRepository.deleteAll();
        Place p = placeRepository.save(Place.of(
                "상도1동 무더위쉼터", PlaceCategory.COOLING_SHELTER, "서울 동작구 성대로 100",
                GeoUtils.point(LAT, LNG), "test", "score-1"));
        placeId = p.getId();
    }

    private void save(ReportType type, OffsetDateTime expiresAt) {
        reportRepository.save(Report.anonymous(placeId, type, null, true, expiresAt));
    }

    @Test
    @DisplayName("제보 없는 장소 상세 → survival.grade=UNKNOWN(정보 부족), 신호 0")
    void detailWithoutReportsIsUnknown() throws Exception {
        mvc.perform(get("/places/" + placeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.survival.grade").value("UNKNOWN"))
                .andExpect(jsonPath("$.survival.reportCount").value(0))
                .andExpect(jsonPath("$.survival.freshnessScore").value(0.0))
                .andExpect(jsonPath("$.survival.comfortScore").value(0.0))
                .andExpect(jsonPath("$.survival.riskScore").value(0.0))
                .andExpect(jsonPath("$.survival.distanceScore").doesNotExist()); // 단건은 중심점 없음
    }

    @Test
    @DisplayName("신선한 긍정 제보(시원+자리) → grade=GOOD, comfort·freshness 양수")
    void freshPositiveIsGood() throws Exception {
        save(ReportType.COOL, OffsetDateTime.now().plusHours(3));
        save(ReportType.SEAT_OK, OffsetDateTime.now().plusHours(2));

        mvc.perform(get("/places/" + placeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.survival.grade").value("GOOD"))
                .andExpect(jsonPath("$.survival.reportCount").value(2))
                .andExpect(jsonPath("$.survival.freshnessScore").value(1.0))   // 방금 생성 → 0~1h 버킷
                .andExpect(jsonPath("$.survival.comfortScore").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.survival.riskScore").value(0.0));
    }

    @Test
    @DisplayName("신선한 부정 제보(침수)만 있으면 리스크로 점수가 내려가 grade=OKAY")
    void freshNegativeIsOkay() throws Exception {
        save(ReportType.FLOOD, OffsetDateTime.now().plusHours(12));

        mvc.perform(get("/places/" + placeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.survival.grade").value("OKAY"))
                .andExpect(jsonPath("$.survival.reportCount").value(1))
                .andExpect(jsonPath("$.survival.riskScore").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$.survival.comfortScore").value(0.0));
    }

    @Test
    @DisplayName("만료된 제보는 뷰(WHERE expires_at>now())에서 빠져 grade=UNKNOWN으로 되돌아간다")
    void expiredReportsExcludedFromScore() throws Exception {
        save(ReportType.COOL, OffsetDateTime.now().minusHours(1)); // 이미 만료

        mvc.perform(get("/places/" + placeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.survival.grade").value("UNKNOWN"))
                .andExpect(jsonPath("$.survival.reportCount").value(0));
    }

    @Test
    @DisplayName("반경 검색은 거리 성분을 포함 → survival.distanceScore가 non-null로 온다")
    void radiusIncludesDistanceScore() throws Exception {
        save(ReportType.COOL, OffsetDateTime.now().plusHours(3));

        mvc.perform(get("/places")
                        .param("lat", String.valueOf(LAT))
                        .param("lng", String.valueOf(LNG))
                        .param("radius", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].survival.distanceScore").value(org.hamcrest.Matchers.greaterThan(0.0)))
                .andExpect(jsonPath("$[0].survival.grade").value("GOOD")); // 매우 가깝고 신선한 긍정
    }
}
