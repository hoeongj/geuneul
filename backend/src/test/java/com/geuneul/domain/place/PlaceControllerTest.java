package com.geuneul.domain.place;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨트롤러 검증 규칙 단위 테스트 (MockMvc, DB 불필요) —
 * 공간쿼리 정확성은 PlaceSpatialQueryIT, 여기선 입력 검증·모드 분기·클램프만.
 */
@WebMvcTest(PlaceController.class)
class PlaceControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlaceSearchService placeSearchService;

    @Test
    @DisplayName("lat/lng도 bounds도 없으면 400")
    void missingModeParamsIs400() throws Exception {
        mvc.perform(get("/places")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("반경 모드: 기본 radius 800m로 서비스 호출")
    void radiusModeUsesDefault() throws Exception {
        given(placeSearchService.searchRadius(anyDouble(), anyDouble(), anyDouble(), isNull(), eq(100)))
                .willReturn(List.of());

        mvc.perform(get("/places").param("lat", "37.4963").param("lng", "126.9575"))
                .andExpect(status().isOk());

        then(placeSearchService).should()
                .searchRadius(eq(37.4963), eq(126.9575), eq(800.0), isNull(), eq(100));
    }

    @Test
    @DisplayName("radius가 최대치(5km) 초과면 400")
    void radiusOverMaxIs400() throws Exception {
        mvc.perform(get("/places")
                        .param("lat", "37.4963").param("lng", "126.9575").param("radius", "9999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("radius가 Infinity면 400")
    void infiniteRadiusIs400() throws Exception {
        mvc.perform(get("/places")
                        .param("lat", "37.4963").param("lng", "126.9575").param("radius", "Infinity"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("좌표 범위 밖(lat>90)이면 400")
    void invalidLatIs400() throws Exception {
        mvc.perform(get("/places").param("lat", "95").param("lng", "126.9575"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("bounds 형식 오류(항목 3개)면 400")
    void malformedBoundsIs400() throws Exception {
        mvc.perform(get("/places").param("bounds", "126.9,37.4,127.0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("bounds가 뒤집혀 있으면(west>east) 400")
    void invertedBoundsIs400() throws Exception {
        mvc.perform(get("/places").param("bounds", "127.0,37.4,126.9,37.5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("bounds 스팬이 위도/경도 5도를 넘으면 400")
    void tooWideBoundsIs400() throws Exception {
        mvc.perform(get("/places").param("bounds", "126.0,37.0,132.0,38.0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("bounds 모드: 4값 파싱해서 서비스 호출")
    void boundsModeParsesBox() throws Exception {
        given(placeSearchService.searchBounds(anyDouble(), anyDouble(), anyDouble(), anyDouble(), isNull(), eq(100)))
                .willReturn(List.of());

        mvc.perform(get("/places").param("bounds", "126.93,37.49,126.97,37.52"))
                .andExpect(status().isOk());

        then(placeSearchService).should()
                .searchBounds(eq(126.93), eq(37.49), eq(126.97), eq(37.52), isNull(), eq(100));
    }

    @Test
    @DisplayName("nearest: limit은 최대 50으로 클램프된다")
    void nearestLimitClamped() throws Exception {
        given(placeSearchService.searchNearest(anyDouble(), anyDouble(), isNull(), eq(50)))
                .willReturn(List.of());

        mvc.perform(get("/places/nearest")
                        .param("lat", "37.4963").param("lng", "126.9575").param("limit", "999"))
                .andExpect(status().isOk());

        then(placeSearchService).should().searchNearest(eq(37.4963), eq(126.9575), isNull(), eq(50));
    }
}
