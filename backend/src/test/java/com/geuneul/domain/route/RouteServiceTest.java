package com.geuneul.domain.route;

import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.route.dto.RouteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
        // 기본: corridor 그늘/실내 없음(F4). shade를 검증하는 테스트는 개별로 재스텁한다.
        when(placeRepository.findShadeAlongCorridor(anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of());
    }

    private static RouteShadeView shade(long id, String name, String category, double lat, double lng) {
        return new RouteShadeView() {
            @Override public Long getId() { return id; }
            @Override public String getName() { return name; }
            @Override public String getCategory() { return category; }
            @Override public Double getLat() { return lat; }
            @Override public Double getLng() { return lng; }
        };
    }

    private static RouteWaypointView view(double lat, double lng, long id, String name, String category,
                                          double distFrom, double distTo) {
        return new RouteWaypointView() {
            @Override public long getPlaceId() { return id; }
            @Override public String getName() { return name; }
            @Override public String getCategory() { return category; }
            @Override public double getLat() { return lat; }
            @Override public double getLng() { return lng; }
            @Override public String getAddress() { return "서울 동작구"; }
            @Override public double getDistFromM() { return distFrom; }
            @Override public double getDistToM() { return distTo; }
        };
    }

    @Test
    @DisplayName("경유 화장실이 있으면 3점 폴리라인 + 경유 총거리(출발거리+도착거리) + 경유지 카테고리")
    void withWaypoint() {
        when(placeRepository.findBestWaypointByCategories(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.of(view(37.51, 126.94, 185L, "노량진역 화장실", "TOILET", 300, 400)));

        RouteResponse res = service.toiletRoute(37.50, 126.93, 37.52, 126.95);

        assertThat(res.waypoint()).isNotNull();
        assertThat(res.waypoint().placeId()).isEqualTo(185L);
        assertThat(res.waypoint().name()).isEqualTo("노량진역 화장실");
        assertThat(res.waypoint().category()).isEqualTo("TOILET"); // 미니맵 아이콘 구분용(C4)
        assertThat(res.polyline()).hasSize(3);                 // 출발·화장실·도착
        assertThat(res.routeDistanceM()).isEqualTo(700.0);     // 300 + 400
        assertThat(res.mode()).isEqualTo("straight");
    }

    @Test
    @DisplayName("경유지가 없으면 2점 직선 폴리라인 + 경유거리=직선거리")
    void withoutWaypoint() {
        when(placeRepository.findBestWaypointByCategories(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.empty());

        RouteResponse res = service.toiletRoute(37.50, 126.93, 37.52, 126.95);

        assertThat(res.waypoint()).isNull();
        assertThat(res.polyline()).hasSize(2);                 // 출발·도착
        assertThat(res.routeDistanceM()).isEqualTo(res.directDistanceM());
        assertThat(res.origin().placeId()).isNull();
        assertThat(res.destination().placeId()).isNull();
    }

    @Test
    @DisplayName("그늘 경유 경로 — 쿨링쉼터 경유지가 3점 폴리라인 + 카테고리 노출(C4, F3 대칭)")
    void shadeRouteInsertsShelterWaypoint() {
        when(placeRepository.findBestWaypointByCategories(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.of(view(37.51, 126.94, 42L, "상도동 무더위쉼터", "COOLING_SHELTER", 250, 350)));

        RouteResponse res = service.shadeRoute(37.50, 126.93, 37.52, 126.95);

        assertThat(res.waypoint()).isNotNull();
        assertThat(res.waypoint().placeId()).isEqualTo(42L);
        assertThat(res.waypoint().category()).isEqualTo("COOLING_SHELTER"); // 화장실 아이콘 아님
        assertThat(res.polyline()).hasSize(3);
        assertThat(res.routeDistanceM()).isEqualTo(600.0); // 250 + 350
    }

    @Test
    @DisplayName("경로 corridor 안의 그늘/실내가 shadeSpots로 실린다(F4)")
    void includesShadeSpots() {
        when(placeRepository.findBestWaypointByCategories(anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenReturn(Optional.empty());
        when(placeRepository.findShadeAlongCorridor(anyString(), anyString(), anyDouble(), anyInt()))
                .thenReturn(List.of(shade(10L, "상도도서관", "LIBRARY", 37.505, 126.94)));

        RouteResponse res = service.toiletRoute(37.50, 126.93, 37.52, 126.95);

        assertThat(res.shadeSpots()).hasSize(1);
        assertThat(res.shadeSpots().get(0).placeId()).isEqualTo(10L);
        assertThat(res.shadeSpots().get(0).name()).isEqualTo("상도도서관");
        assertThat(res.shadeSpots().get(0).category()).isEqualTo("LIBRARY");
        assertThat(res.shadeSpots().get(0).lat()).isEqualTo(37.505);
    }
}
