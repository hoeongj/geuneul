package com.geuneul.domain.route;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.global.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 화장실 포함 경로 IT (B2, ADR-0019) — 실 PostGIS에서 경유지 선택(ST_DWithin corridor + detour 최소)을
 * API 응답까지 관통 검증한다. 폴리라인은 직선 MVP(mode=straight). 화장실 밀도가 낮은 corridor는 경유지 없이 폴백.
 */
@AutoConfigureMockMvc
class RouteToiletIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PlaceRepository placeRepository;

    @BeforeEach
    void setUp() {
        placeRepository.deleteAll();
    }

    private void toilet(String name, double lat, double lng, String extId) {
        placeRepository.save(Place.of(name, PlaceCategory.TOILET, "서울 동작구",
                GeoUtils.point(lat, lng), "test", extId));
    }

    @Test
    @DisplayName("출발-도착 사이 화장실이 경유지로 끼워지고 폴리라인 3점(mode=straight)")
    void insertsToiletWaypoint() throws Exception {
        toilet("경유 화장실", 37.510, 126.940, "rt-mid");     // O-D 사이
        toilet("먼 화장실", 35.100, 129.000, "rt-far");        // 부산(corridor 밖)

        mvc.perform(get("/routes/toilet")
                        .param("fromLat", "37.500").param("fromLng", "126.930")
                        .param("toLat", "37.520").param("toLng", "126.950"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waypoint").exists())
                .andExpect(jsonPath("$.waypoint.name").value("경유 화장실"))
                .andExpect(jsonPath("$.polyline.length()").value(3))
                .andExpect(jsonPath("$.mode").value("straight"))
                .andExpect(jsonPath("$.routeDistanceM").value(org.hamcrest.Matchers.greaterThan(0.0)));
    }

    @Test
    @DisplayName("corridor 안에 화장실이 없으면 경유지 없이 직선 2점")
    void fallsBackWithoutToilet() throws Exception {
        toilet("먼 화장실", 35.100, 129.000, "rt-far"); // 부산만 있음(서울 경로 corridor 밖)

        mvc.perform(get("/routes/toilet")
                        .param("fromLat", "37.500").param("fromLng", "126.930")
                        .param("toLat", "37.520").param("toLng", "126.950"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waypoint").doesNotExist())
                .andExpect(jsonPath("$.polyline.length()").value(2));
    }

    @Test
    @DisplayName("해외/뒤집힌 좌표는 400")
    void rejectsOutOfKorea() throws Exception {
        mvc.perform(get("/routes/toilet")
                        .param("fromLat", "48.85").param("fromLng", "2.35") // 파리
                        .param("toLat", "37.52").param("toLng", "126.95"))
                .andExpect(status().isBadRequest());
    }
}
