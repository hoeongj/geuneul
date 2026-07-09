package com.geuneul.domain.place;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.global.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 장소 상세 시설 등급화 노출 IT (ADR-0005 §④) — place_features가 등급화돼 상세 응답에 실리고,
 * present=false(부재) 속성은 제외되는지 검증. 쓰기 엔티티가 없어 JdbcTemplate으로 place_features를 직접 심는다.
 */
@AutoConfigureMockMvc
class PlaceFeatureDetailIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    JdbcTemplate jdbc;

    private Long placeId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM place_features");
        placeRepository.deleteAll();
        Place saved = placeRepository.save(Place.of(
                "노들서가", PlaceCategory.STUDY_CAFE, "서울 동작구",
                GeoUtils.point(37.5124, 126.9530), "test", "pf-1"));
        placeId = saved.getId();
    }

    private void feature(String type, String value) {
        jdbc.update("INSERT INTO place_features(place_id, feature_type, value, source, confidence) "
                + "VALUES (?, ?, ?, 'test', 0.7)", placeId, type, value);
    }

    @Test
    @DisplayName("상세 응답에 등급화된 시설이 실리고, 부재(present=false) 속성은 제외된다")
    void detailExposesGradedFeatures() throws Exception {
        feature("outlet", "many");        // MANY, 콘센트 많음
        feature("noise_level", "quiet");  // QUIET, 조용함
        feature("study_ok", "true");      // PRESENT, 공부 가능
        feature("wifi", "false");         // present=false → 제외

        mvc.perform(get("/places/" + placeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features.length()").value(3))   // wifi(false) 제외
                .andExpect(jsonPath("$.features[?(@.type=='outlet')].level").value("MANY"))
                .andExpect(jsonPath("$.features[?(@.type=='outlet')].label").value("콘센트 많음"))
                .andExpect(jsonPath("$.features[?(@.type=='noise_level')].label").value("조용함"))
                .andExpect(jsonPath("$.features[?(@.type=='wifi')]").isEmpty());
    }

    @Test
    @DisplayName("시설이 없으면 features는 빈 배열")
    void emptyWhenNoFeatures() throws Exception {
        mvc.perform(get("/places/" + placeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(0));
    }
}
