package com.geuneul.domain.recommend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 추천 컨트롤러 검증 규칙 단위 테스트(MockMvc, DB 불필요) —
 * 랭킹 정확성은 RecommendationIT, 여기선 입력 검증·기본값·클램프·시나리오 매핑만.
 */
@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RecommendationService recommendationService;

    @Test
    @DisplayName("scenario 파라미터가 없으면 400")
    void missingScenarioIs400() throws Exception {
        mvc.perform(get("/recommendations").param("lat", "37.4963").param("lng", "126.9575"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지원하지 않는 scenario면 400이고 서비스는 호출되지 않는다")
    void unknownScenarioIs400() throws Exception {
        mvc.perform(get("/recommendations")
                        .param("lat", "37.4963").param("lng", "126.9575").param("scenario", "nope"))
                .andExpect(status().isBadRequest());

        then(recommendationService).should(never())
                .recommend(any(), anyDouble(), anyDouble(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("좌표 범위 밖(lat>90)이면 400")
    void invalidLatIs400() throws Exception {
        mvc.perform(get("/recommendations")
                        .param("lat", "95").param("lng", "126.9575").param("scenario", "restroom"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("radius가 최대치(5km) 초과면 400")
    void radiusOverMaxIs400() throws Exception {
        mvc.perform(get("/recommendations")
                        .param("lat", "37.4963").param("lng", "126.9575")
                        .param("scenario", "restroom").param("radius", "9999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("기본값: radius 2000m·limit 5로 서비스 호출, limit은 최대 20으로 클램프")
    void defaultsAndLimitClamp() throws Exception {
        given(recommendationService.recommend(eq(RecommendationScenario.RESTROOM),
                anyDouble(), anyDouble(), eq(2_000.0), eq(20))).willReturn(List.of());

        mvc.perform(get("/recommendations")
                        .param("lat", "37.4963").param("lng", "126.9575")
                        .param("scenario", "restroom").param("limit", "999"))
                .andExpect(status().isOk());

        then(recommendationService).should()
                .recommend(eq(RecommendationScenario.RESTROOM), eq(37.4963), eq(126.9575), eq(2_000.0), eq(20));
    }
}
