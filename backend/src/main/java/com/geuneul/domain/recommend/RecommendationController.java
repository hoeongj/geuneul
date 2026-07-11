package com.geuneul.domain.recommend;

import com.geuneul.domain.recommend.dto.RecommendationResponse;
import com.geuneul.global.web.ApiRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 추천 API (docs/SPEC.md §9, ADR-0008) — survival_score에 시나리오 가중을 얹은 랭킹.
 * "지금 30분 버틸 곳 / 화장실 급할 때 / 비 피할 곳"을 현재 위치 기준으로 정렬해 준다.
 */
@Tag(name = "Recommendations", description = "시나리오 추천 — survival_score 시나리오 가중 랭킹(rest30 · restroom · rain · focus · longstay)")
@RestController
public class RecommendationController {

    static final double DEFAULT_RADIUS_M = 2_000;
    static final double MAX_RADIUS_M = 5_000;
    static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 20;

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Operation(summary = "시나리오 추천",
            description = """
                    현재 위치(lat,lng) 기준으로 시나리오에 맞는 장소를 적합도 순으로 준다.
                    scenario: rest30(잠깐 쉬어갈 곳) | restroom(화장실 급함) | rain(비 피할 곳)
                             | focus(집중해서 공부·작업) | longstay(오래 버틸 곳).
                    각 결과는 지도와 동일한 survival 배지 + 시나리오 적합도(matchScore) + 근거(reason)를 담는다.
                    """)
    @GetMapping("/recommendations")
    public List<RecommendationResponse> recommend(
            @RequestParam double lat,
            @RequestParam double lng,
            @Parameter(description = "rest30 | restroom | rain | focus | longstay") @RequestParam String scenario,
            @Parameter(description = "반경(m), 기본 2000, 최대 5000") @RequestParam(required = false) Double radius,
            @Parameter(description = "최대 결과 수, 기본 5, 최대 20") @RequestParam(defaultValue = "5") int limit) {

        ApiRequests.requireValidLatLng(lat, lng);
        RecommendationScenario sc = RecommendationScenario.fromParam(scenario);

        double radiusMeters = radius == null ? DEFAULT_RADIUS_M : radius;
        ApiRequests.requireRadiusWithin(radiusMeters, MAX_RADIUS_M);
        int safeLimit = ApiRequests.clampLimit(limit, MAX_LIMIT);

        return recommendationService.recommend(sc, lat, lng, radiusMeters, safeLimit);
    }
}
