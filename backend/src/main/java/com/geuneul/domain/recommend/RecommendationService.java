package com.geuneul.domain.recommend;

import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.place.ScoredPlaceView;
import com.geuneul.domain.place.SurvivalScore;
import com.geuneul.domain.place.dto.PlaceResponse;
import com.geuneul.domain.recommend.dto.RecommendationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 추천 랭킹 (ADR-0008) — <b>2단 검색</b>: PostGIS 공간 인덱스로 후보를 선필터하고,
 * 시나리오 가중치로 후보 풀을 재랭킹한다(retrieval → re-rank, 2025 표준 패턴).
 *
 * <ol>
 *   <li><b>선필터(DB):</b> 시나리오 카테고리 집합 안에서 반경 내 "가까운 순 상위 pool"을 뽑는다
 *       (ST_DWithin geography + KNN 정렬, 인덱스 경로 유지). 화장실처럼 조밀한 카테고리도
 *       pool이 후보를 충분히 담아 재랭킹 재료가 된다.</li>
 *   <li><b>재랭킹(앱):</b> 각 후보를 시나리오 가중치로 조립한 matchScore로 내림차순 정렬(동점은 거리 오름차순).</li>
 * </ol>
 *
 * <p>pool을 limit보다 넉넉히(×5, [50,200]) 뽑는 이유: 거리순 선필터만으로는 "가깝지만 붐빔"이
 * "조금 멀지만 시원함"을 이길 수 있어, 재랭킹이 뒤집을 여지를 확보한다. pool 상한(200)은
 * 앱 재랭킹 비용을 묶는다(대량 트래픽 튜닝은 P4).
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    static final int POOL_MULTIPLIER = 5;
    static final int MIN_POOL = 50;
    static final int MAX_POOL = 200;

    private final PlaceRepository placeRepository;

    public RecommendationService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    public List<RecommendationResponse> recommend(RecommendationScenario scenario,
                                                  double lat, double lng, double radiusMeters, int limit) {
        int pool = Math.min(MAX_POOL, Math.max(MIN_POOL, limit * POOL_MULTIPLIER));

        List<ScoredPlaceView> candidates = placeRepository.findWithinRadiusScoredByCategories(
                lat, lng, radiusMeters, scenario.categoriesCsv(), pool);

        return candidates.stream()
                .map(v -> toItem(scenario, v, radiusMeters))
                .sorted(Comparator.comparingInt(RecommendationResponse::matchScore).reversed()
                        .thenComparing(RecommendationService::distanceOrMax))
                .limit(limit)
                .toList();
    }

    private static RecommendationResponse toItem(RecommendationScenario scenario,
                                                 ScoredPlaceView v, double radiusMeters) {
        PlaceResponse place = PlaceResponse.of(v, radiusMeters); // canonical survival(§5) 포함
        int matchScore = SurvivalScore.of(
                scenario.weights(), v.getDistanceM(), radiusMeters, v.getReportCount(),
                v.getFreshnessScore(), v.getComfortScore(), v.getRiskScore()).score();
        String reason = RecommendationReason.of(v.getReportCount(), v.getComfortScore(), v.getRiskScore());
        return new RecommendationResponse(place, matchScore, reason);
    }

    /** 동점 타이브레이크용 거리(null=무한대). 반경 검색이라 실제로는 항상 non-null. */
    private static double distanceOrMax(RecommendationResponse r) {
        Double d = r.place().distanceM();
        return d == null ? Double.MAX_VALUE : d;
    }
}
