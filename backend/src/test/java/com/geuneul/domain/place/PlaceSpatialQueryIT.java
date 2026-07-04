package com.geuneul.domain.place;

import com.geuneul.AbstractIntegrationTest;
import com.geuneul.global.geo.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공간쿼리 정확성 IT — 실 PostGIS에서 반경/kNN/bounds를 실좌표로 검증한다.
 * 기준점: 숭실대 정문 (37.4963, 126.9575). 테스트 데이터 거리(대략):
 *   - 숭실대입구역 ≈ 330m / 상도역 ≈ 1.1km / 강남역 ≈ 6.2km
 */
class PlaceSpatialQueryIT extends AbstractIntegrationTest {

    private static final double SSU_LAT = 37.4963;
    private static final double SSU_LNG = 126.9575;

    @Autowired
    PlaceRepository placeRepository;

    @BeforeEach
    void setUp() {
        placeRepository.deleteAll(); // R__seed 데이터 제거 — 테스트는 자기 데이터만 본다
        placeRepository.save(place("숭실대입구역", PlaceCategory.UNDERGROUND, 37.4963, 126.9538, "t-1"));
        placeRepository.save(place("상도역 화장실", PlaceCategory.TOILET, 37.5028, 126.9479, "t-2"));
        placeRepository.save(place("강남역", PlaceCategory.UNDERGROUND, 37.4979, 127.0276, "t-3"));
    }

    private static Place place(String name, PlaceCategory category, double lat, double lng, String extId) {
        return Place.of(name, category, null, GeoUtils.point(lat, lng), "test", extId);
    }

    @Test
    @DisplayName("반경 검색(ST_DWithin geography): 1.5km 안에는 2곳, 가까운 순으로 정렬된다")
    void radiusSearchFiltersAndOrdersByDistance() {
        List<ScoredPlaceView> result = placeRepository.findWithinRadiusScored(SSU_LAT, SSU_LNG, 1_500, null, 100);

        assertThat(result).extracting(ScoredPlaceView::getName)
                .containsExactly("숭실대입구역", "상도역 화장실"); // 강남역(6.2km) 제외 + 거리순
    }

    @Test
    @DisplayName("반경 검색: 거리(m)를 DB가 함께 반환하고, 그 값이 정렬 순서와 일치한다(오름차순)")
    void radiusReturnsDistanceConsistentWithOrder() {
        List<ScoredPlaceView> result = placeRepository.findWithinRadiusScored(SSU_LAT, SSU_LNG, 2_000, null, 100);

        // 표시 거리 = 정렬 기준(둘 다 타원체) → distanceM이 단조 증가해야 한다.
        assertThat(result).extracting(ScoredPlaceView::getDistanceM).isSorted();
        // 첫 결과(숭실대입구역)는 실제로 약 330m 근처.
        assertThat(result.get(0).getDistanceM()).isBetween(250.0, 450.0);
    }

    @Test
    @DisplayName("반경 검색: 500m로 좁히면 숭실대입구역만 남는다 (미터 단위 반경이 실제로 동작)")
    void radiusIsInMeters() {
        List<ScoredPlaceView> result = placeRepository.findWithinRadiusScored(SSU_LAT, SSU_LNG, 500, null, 100);

        assertThat(result).extracting(ScoredPlaceView::getName).containsExactly("숭실대입구역");
    }

    @Test
    @DisplayName("카테고리 필터: TOILET만 요청하면 반경 내 화장실만 반환한다")
    void radiusSearchWithCategoryFilter() {
        List<ScoredPlaceView> result = placeRepository.findWithinRadiusScored(
                SSU_LAT, SSU_LNG, 1_500, PlaceCategory.TOILET.name(), 100);

        assertThat(result).extracting(ScoredPlaceView::getName).containsExactly("상도역 화장실");
    }

    @Test
    @DisplayName("제보 없는 장소는 스코어드 검색에서 신호가 0으로 온다 (LEFT JOIN COALESCE)")
    void scoredWithoutReportsYieldsZeroSignals() {
        ScoredPlaceView near = placeRepository.findWithinRadiusScored(SSU_LAT, SSU_LNG, 500, null, 100).get(0);

        assertThat(near.getReportCount()).isZero();
        assertThat(near.getFreshnessScore()).isZero();
        assertThat(near.getComfortScore()).isZero();
        assertThat(near.getRiskScore()).isZero();
        assertThat(near.getDistanceM()).isNotNull(); // 반경 검색은 거리 non-null
    }

    @Test
    @DisplayName("kNN(<->): 반경 제한 없이 가까운 순 — 강남역도 3순위로 포함된다")
    void knnOrdersAllByDistance() {
        List<PlaceDistanceView> result = placeRepository.findNearest(SSU_LAT, SSU_LNG, null, 3);

        assertThat(result).extracting(PlaceDistanceView::getName)
                .containsExactly("숭실대입구역", "상도역 화장실", "강남역");
        assertThat(result).extracting(PlaceDistanceView::getDistanceM).isSorted();
    }

    @Test
    @DisplayName("kNN + 카테고리: 가장 가까운 화장실 1곳 — '화장실 급할 때' 시나리오")
    void knnNearestToilet() {
        List<PlaceDistanceView> result = placeRepository.findNearest(SSU_LAT, SSU_LNG, PlaceCategory.TOILET.name(), 1);

        assertThat(result).extracting(PlaceDistanceView::getName).containsExactly("상도역 화장실");
    }

    @Test
    @DisplayName("bounds(&&): 상도역만 포함하는 박스는 상도역만 반환한다 (거리 없음 → distanceM=null)")
    void boundsSearchReturnsOnlyInsideBox() {
        List<ScoredPlaceView> result = placeRepository.findInBoundsScored(126.945, 37.500, 126.950, 37.505, null, 100);

        assertThat(result).extracting(ScoredPlaceView::getName).containsExactly("상도역 화장실");
        assertThat(result.get(0).getDistanceM()).isNull(); // bounds는 중심점이 없어 거리 미반환
    }
}
