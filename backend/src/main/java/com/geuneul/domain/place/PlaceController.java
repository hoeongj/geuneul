package com.geuneul.domain.place;

import com.geuneul.domain.place.dto.PlaceResponse;
import com.geuneul.global.web.ApiRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 지도/장소 조회 API (docs/SPEC.md §9). PostGIS 인덱스 경로는 PlaceRepository 주석 참고.
 */
@Tag(name = "Places", description = "장소 공간검색 — 반경(ST_DWithin) · bounds · 최근접(kNN)")
@RestController
public class PlaceController {

    static final double MAX_RADIUS_M = 5_000;
    static final int MAX_LIMIT = 500;
    static final int MAX_NEAREST_LIMIT = 50;

    private final PlaceSearchService placeSearchService;

    public PlaceController(PlaceSearchService placeSearchService) {
        this.placeSearchService = placeSearchService;
    }

    @Operation(summary = "장소 검색 (반경 또는 bounds)",
            description = """
                    두 모드 중 하나로 호출한다.
                    ① 반경: lat, lng, radius(m) — 가까운 순 정렬 + 거리(distanceM) 포함.
                    ② bounds: bounds=west,south,east,north (경도,위도,경도,위도) — 지도 뷰포트 마커 조회.
                    """)
    @GetMapping("/places")
    public List<PlaceResponse> search(
            @Parameter(description = "중심 위도 (반경 모드)") @RequestParam(required = false) Double lat,
            @Parameter(description = "중심 경도 (반경 모드)") @RequestParam(required = false) Double lng,
            @Parameter(description = "반경(m), 기본 800, 최대 5000") @RequestParam(defaultValue = "800") double radius,
            @Parameter(description = "west,south,east,north (bounds 모드)") @RequestParam(required = false) String bounds,
            @Parameter(description = "카테고리 필터") @RequestParam(required = false) PlaceCategory category,
            @Parameter(description = "최대 결과 수, 기본 100, 최대 500") @RequestParam(defaultValue = "100") int limit) {

        int safeLimit = ApiRequests.clampLimit(limit, MAX_LIMIT);

        if (bounds != null && !bounds.isBlank()) {
            double[] box = ApiRequests.parseBounds(bounds);
            return placeSearchService.searchBounds(box[0], box[1], box[2], box[3], category, safeLimit);
        }
        if (lat == null || lng == null) {
            throw new ResponseStatusException(BAD_REQUEST, "lat/lng (반경 모드) 또는 bounds 중 하나는 필수입니다");
        }
        ApiRequests.requireValidLatLng(lat, lng);
        ApiRequests.requireRadiusWithin(radius, MAX_RADIUS_M);
        return placeSearchService.searchRadius(lat, lng, radius, category, safeLimit);
    }

    @Operation(summary = "최근접 장소 (kNN)",
            description = "반경 제한 없이 가까운 순 상위 N개. \"화장실 급할 때\" 시나리오의 기반 쿼리.")
    @GetMapping("/places/nearest")
    public List<PlaceResponse> nearest(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) PlaceCategory category,
            @Parameter(description = "기본 5, 최대 50") @RequestParam(defaultValue = "5") int limit) {
        ApiRequests.requireValidLatLng(lat, lng);
        return placeSearchService.searchNearest(lat, lng, category, ApiRequests.clampLimit(limit, MAX_NEAREST_LIMIT));
    }

    @Operation(summary = "장소 단건 조회")
    @GetMapping("/places/{id}")
    public PlaceResponse get(@PathVariable long id) {
        return placeSearchService.getById(id);
    }
}
