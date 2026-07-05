package com.geuneul.domain.recommend;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.report.Report;
import com.geuneul.domain.report.ReportRepository;
import com.geuneul.domain.report.ReportType;
import com.geuneul.global.geo.GeoUtils;
import org.hamcrest.Matchers;
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
 * 추천(/recommendations) 엔드투엔드 IT — 실 PostGIS에서 2단 검색(공간 선필터 → 시나리오 재랭킹)을
 * API 응답까지 관통 검증한다(ADR-0008). 기준점: 숭실대 정문 (37.4963, 126.9575).
 * 위도 0.001°≈111m를 이용해 near/far를 배치한다.
 */
@AutoConfigureMockMvc
class RecommendationIT extends AbstractIntegrationTest {

    private static final double LAT = 37.4963;
    private static final double LNG = 126.9575;

    @Autowired
    MockMvc mvc;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    ReportRepository reportRepository;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        placeRepository.deleteAll();
    }

    private long savePlace(String name, PlaceCategory category, double lat, double lng, String extId) {
        return placeRepository.save(Place.of(name, category, "주소 " + name,
                GeoUtils.point(lat, lng), "test", extId)).getId();
    }

    private void saveReport(long placeId, ReportType type, OffsetDateTime expiresAt) {
        reportRepository.save(Report.anonymous(placeId, type, null, true, expiresAt));
    }

    @Test
    @DisplayName("화장실 급함: 가까운 무제보 화장실이 먼 유제보 화장실보다 위(distance 압도) + matchScore·survival 동반")
    void restroomRanksProximityFirst() throws Exception {
        savePlace("가까운 화장실", PlaceCategory.TOILET, LAT + 0.0009, LNG, "t-near"); // ≈100m, 무제보
        long far = savePlace("먼 화장실", PlaceCategory.TOILET, LAT + 0.011, LNG, "t-far"); // ≈1.2km
        saveReport(far, ReportType.RESTROOM_CLEAN, OffsetDateTime.now().plusHours(72));
        saveReport(far, ReportType.WATER_OK, OffsetDateTime.now().plusHours(72));

        mvc.perform(get("/recommendations")
                        .param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG))
                        .param("scenario", "restroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].place.name").value("가까운 화장실"))
                .andExpect(jsonPath("$[0].matchScore").value(Matchers.greaterThan(0)))
                .andExpect(jsonPath("$[0].place.survival.grade").exists()) // 지도와 동일 배지 동반
                .andExpect(jsonPath("$[0].reason").value("실시간 제보 없음"))
                .andExpect(jsonPath("$[1].place.name").value("먼 화장실"));
    }

    @Test
    @DisplayName("화장실 급함: 시나리오 카테고리(TOILET) 밖의 장소는 제외된다(가까운 도서관도 안 나옴)")
    void restroomExcludesOtherCategories() throws Exception {
        savePlace("가까운 화장실", PlaceCategory.TOILET, LAT + 0.0009, LNG, "t-near");
        savePlace("더 가까운 도서관", PlaceCategory.LIBRARY, LAT + 0.0005, LNG, "l-near"); // 더 가깝지만 카테고리 밖

        mvc.perform(get("/recommendations")
                        .param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG))
                        .param("scenario", "restroom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].place.name").value("가까운 화장실"));
    }

    @Test
    @DisplayName("비 피할 곳: 신선한 침수 제보 장소는 같은 거리의 무제보 장소보다 아래로 강등된다(risk 페널티↑)")
    void rainDemotesFloodedBelowDry() throws Exception {
        savePlace("멀쩡한 쉼터", PlaceCategory.COOLING_SHELTER, LAT - 0.0018, LNG, "s-dry"); // ≈200m, 무제보
        long flooded = savePlace("침수 쉼터", PlaceCategory.COOLING_SHELTER, LAT - 0.0020, LNG, "s-flood"); // ≈220m
        saveReport(flooded, ReportType.FLOOD, OffsetDateTime.now().plusHours(12));

        mvc.perform(get("/recommendations")
                        .param("lat", String.valueOf(LAT)).param("lng", String.valueOf(LNG))
                        .param("scenario", "rain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].place.name").value("멀쩡한 쉼터"))
                .andExpect(jsonPath("$[1].place.name").value("침수 쉼터"))
                .andExpect(jsonPath("$[1].reason").value("최근 주의 제보 있음"));
    }
}
