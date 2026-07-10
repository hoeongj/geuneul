package com.geuneul.domain.route;

import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.route.dto.RouteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RouteService 조립 단위테스트 — DB 없이 로컬에서 항상 돈다(TS-009 무관).
 * 실 경유지 선택(ST_DWithin·detour 최소)은 RouteToiletIT(CI)가 커버; 여기는 조립·폴백만.
 * 폴리라인 공급자는 실제 StraightLineDirectionsProvider(직선 MVP)를 그대로 쓴다.
 */
class RouteServiceTest {

    private PlaceRepository placeRepository;
    private RouteService service;

    @BeforeEach
    void setUp() {
        placeRepository = mock(PlaceRepository.class);
        service = new RouteService(placeRepository, new StraightLineDirectionsProvider());
    }

    private static RouteWaypointView view(double lat, double lng, long id, String name,
                                          double distFrom, double distTo) {
        return new RouteWaypointView() {
            @Override public long getPlaceId() { return id; }
            @Override public String getName() { return name; }
            @Override public double getLat() { return lat; }
            @Override public double getLng() { return lng; }
            @Override public String getAddress() { return "서울 동작구"; }
            @Override public double getDistFromM() { return distFrom; }
            @Override public double getDistToM() { return distTo; }
        };
    }

    @Test
    @DisplayName("경유 화장실이 있으면 3점 폴리라인 + 경유 총거리(출발거리+도착거리)")
    void withWaypoint() {
        when(placeRepository.findBestToiletWaypoint(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.of(view(37.51, 126.94, 185L, "노량진역 화장실", 300, 400)));

        RouteResponse res = service.toiletRoute(37.50, 126.93, 37.52, 126.95);

        assertThat(res.waypoint()).isNotNull();
        assertThat(res.waypoint().placeId()).isEqualTo(185L);
        assertThat(res.waypoint().name()).isEqualTo("노량진역 화장실");
        assertThat(res.polyline()).hasSize(3);                 // 출발·화장실·도착
        assertThat(res.routeDistanceM()).isEqualTo(700.0);     // 300 + 400
        assertThat(res.mode()).isEqualTo("straight");
    }

    @Test
    @DisplayName("경유 화장실이 없으면 2점 직선 폴리라인 + 경유거리=직선거리")
    void withoutWaypoint() {
        when(placeRepository.findBestToiletWaypoint(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

        RouteResponse res = service.toiletRoute(37.50, 126.93, 37.52, 126.95);

        assertThat(res.waypoint()).isNull();
        assertThat(res.polyline()).hasSize(2);                 // 출발·도착
        assertThat(res.routeDistanceM()).isEqualTo(res.directDistanceM());
        assertThat(res.origin().placeId()).isNull();
        assertThat(res.destination().placeId()).isNull();
    }
}
