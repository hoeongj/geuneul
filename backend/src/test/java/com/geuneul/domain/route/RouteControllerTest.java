package com.geuneul.domain.route;

import com.geuneul.domain.report.ExternalApiRateLimiter;
import com.geuneul.domain.report.ProxyClientResolver;
import com.geuneul.domain.route.dto.LatLng;
import com.geuneul.domain.route.dto.RouteResponse;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteController.class)
class RouteControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RouteService routeService;

    @MockitoBean
    ExternalApiRateLimiter rateLimiter;

    @MockitoBean
    ProxyClientResolver clientResolver;

    @BeforeEach
    void allowByDefault() {
        given(clientResolver.resolve(any())).willReturn("x:127.0.0.1");
        given(rateLimiter.tryAcquire(anyString(), anyString(), anyInt())).willReturn(true);
        given(routeService.toiletRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble())).willReturn(sample());
    }

    @Test
    @DisplayName("정상 요청은 서비스로 위임한다")
    void toiletRouteOk() throws Exception {
        mvc.perform(get("/routes/toilet")
                        .param("fromLat", "37.5").param("fromLng", "127.0")
                        .param("toLat", "37.6").param("toLng", "127.1"))
                .andExpect(status().isOk());

        then(routeService).should().toiletRoute(37.5, 127.0, 37.6, 127.1);
    }

    @Test
    @DisplayName("레이트리밋 초과면 429 — 서비스까지 내려가지 않는다")
    void rateLimitedIs429() throws Exception {
        given(rateLimiter.tryAcquire(anyString(), anyString(), anyInt())).willReturn(false);

        mvc.perform(get("/routes/toilet")
                        .param("fromLat", "37.5").param("fromLng", "127.0")
                        .param("toLat", "37.6").param("toLng", "127.1"))
                .andExpect(status().isTooManyRequests());

        then(routeService).should(never()).toiletRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("NaN 좌표는 400")
    void nanCoordinateIs400() throws Exception {
        mvc.perform(get("/routes/toilet")
                        .param("fromLat", "NaN").param("fromLng", "127.0")
                        .param("toLat", "37.6").param("toLng", "127.1"))
                .andExpect(status().isBadRequest());
    }

    private static RouteResponse sample() {
        return new RouteResponse(
                RouteResponse.RouteStop.coord(37.5, 127.0),
                null,
                RouteResponse.RouteStop.coord(37.6, 127.1),
                List.of(new LatLng(37.5, 127.0), new LatLng(37.6, 127.1)),
                "straight",
                100.0,
                100.0,
                List.of());
    }
}
