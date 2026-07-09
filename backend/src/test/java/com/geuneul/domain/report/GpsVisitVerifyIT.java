package com.geuneul.domain.report;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.place.ScoredPlaceView;
import com.geuneul.global.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GPS 방문 인증 IT (ADR-0005 §④) — 제보자 좌표 vs 장소 ST_DWithin(100m)으로 verified 판정 + V10 뷰 가중.
 * 레이트리미터 회피를 위해 각 POST는 서로 다른 X-Forwarded-For를 쓴다(ReportFlowIT 규약).
 */
@AutoConfigureMockMvc
class GpsVisitVerifyIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReportRepository reportRepository;

    @Autowired
    PlaceRepository placeRepository;

    private static final double LAT = 37.4986;
    private static final double LNG = 126.9531;

    private Long placeId;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        placeRepository.deleteAll();
        Place saved = placeRepository.save(Place.of(
                "상도1동 무더위쉼터", PlaceCategory.COOLING_SHELTER, "서울 동작구 성대로 100",
                GeoUtils.point(LAT, LNG), "test", "gv-1"));
        placeId = saved.getId();
    }

    @Test
    @DisplayName("제보자 좌표가 장소 100m 이내면 verified=true")
    void verifiedWhenWithin100m() throws Exception {
        mvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.1")
                        .content("{\"placeId\":" + placeId + ",\"reportType\":\"COOL\","
                                + "\"lat\":" + LAT + ",\"lng\":" + LNG + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    @DisplayName("제보자 좌표가 멀면(약 1.1km) verified=false")
    void notVerifiedWhenFar() throws Exception {
        mvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.2")
                        .content("{\"placeId\":" + placeId + ",\"reportType\":\"COOL\","
                                + "\"lat\":" + (LAT + 0.01) + ",\"lng\":" + LNG + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    @DisplayName("좌표 미제공 제보는 verified=false (기존 동작)")
    void notVerifiedWhenNoCoords() throws Exception {
        mvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.3")
                        .content("{\"placeId\":" + placeId + ",\"reportType\":\"COOL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    @DisplayName("verified 제보는 survival_score 신호(comfort)를 더 높게 가중한다 (V10, 허위제보 억제)")
    void verifiedReportWeightsHigher() {
        // 장소 B(비교군)
        Place placeB = placeRepository.save(Place.of(
                "상도2동 쉼터", PlaceCategory.COOLING_SHELTER, "서울 동작구",
                GeoUtils.point(LAT + 0.02, LNG), "test", "gv-2"));

        // A: verified COOL 1건 / B: 비검증 COOL 1건 (둘 다 익명·동일 최신성)
        reportRepository.save(Report.of(null, placeId, ReportType.COOL, null, null, true, true,
                OffsetDateTime.now().plusHours(1)));
        reportRepository.save(Report.of(null, placeB.getId(), ReportType.COOL, null, null, true, false,
                OffsetDateTime.now().plusHours(1)));

        ScoredPlaceView a = placeRepository.findByIdScored(placeId).orElseThrow();
        ScoredPlaceView b = placeRepository.findByIdScored(placeB.getId()).orElseThrow();

        // 비검증 0.7(익명 기저) vs 검증 0.91(×1.3) — comfort_score가 더 높아야 한다
        assertThat(a.getComfortScore()).isGreaterThan(b.getComfortScore());
        assertThat(b.getComfortScore()).isCloseTo(0.7, within(1e-9));
        assertThat(a.getComfortScore()).isCloseTo(0.91, within(1e-9));
    }
}
