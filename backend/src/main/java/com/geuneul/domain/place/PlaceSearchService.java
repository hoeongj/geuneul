package com.geuneul.domain.place;

import com.geuneul.domain.ai.AiSummaryService;
import com.geuneul.domain.place.dto.PlaceFeatureResponse;
import com.geuneul.domain.place.dto.PlaceResponse;
import com.geuneul.domain.weather.WeatherService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.function.Supplier;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class PlaceSearchService {

    // 커스텀 관측성 지표(P4, ADR-0014) — 간판(PostGIS 반경 ST_DWithin·kNN <->)의 실측 latency를 남긴다.
    // category 태그는 PlaceCategory enum(고정 카디널리티, ~10개)이라 Prometheus 카디널리티 폭발 위험이 없다.
    private static final String METRIC_SEARCH_RADIUS = "geuneul.place.search.radius";
    private static final String METRIC_SEARCH_NEAREST = "geuneul.place.search.nearest";

    private final PlaceRepository placeRepository;
    private final PlaceFeatureRepository placeFeatureRepository;
    private final WeatherService weatherService;
    private final AiSummaryService aiSummaryService;
    private final MeterRegistry meterRegistry;

    public PlaceSearchService(PlaceRepository placeRepository, PlaceFeatureRepository placeFeatureRepository,
                              WeatherService weatherService, AiSummaryService aiSummaryService,
                              MeterRegistry meterRegistry) {
        this.placeRepository = placeRepository;
        this.placeFeatureRepository = placeFeatureRepository;
        this.weatherService = weatherService;
        this.aiSummaryService = aiSummaryService;
        this.meterRegistry = meterRegistry;
    }

    public List<PlaceResponse> searchRadius(double lat, double lng, double radiusMeters,
                                            PlaceCategory category, int limit) {
        // 반경 검색은 중심점이 있어 거리 성분(0.25)을 포함한 full survival_score를 조립한다.
        Double weatherComfort = weatherComfort(lat, lng); // 요청 1건당 1회(N+1 금지, ADR-0009)
        var rows = timed(METRIC_SEARCH_RADIUS, category, () ->
                placeRepository.findWithinRadiusScored(lat, lng, radiusMeters, name(category), limit));
        return rows.stream()
                .map(v -> PlaceResponse.of(v, radiusMeters, weatherComfort))
                .toList();
    }

    public List<PlaceResponse> searchNearest(double lat, double lng, PlaceCategory category, int limit) {
        // "화장실 급할 때" 팬아웃 — 점수 없이 거리만(마커 색을 쓰지 않는 경로). survival 미계산이라 날씨도 불필요.
        var rows = timed(METRIC_SEARCH_NEAREST, category, () ->
                placeRepository.findNearest(lat, lng, name(category), limit));
        return rows.stream()
                .map(PlaceResponse::of)
                .toList();
    }

    public List<PlaceResponse> searchBounds(double west, double south, double east, double north,
                                            PlaceCategory category, int limit) {
        // 마커: 중심점이 없어 거리 성분을 빼고 "장소 자체 상태"로 점수/등급을 낸다(radiusM=null).
        // 날씨는 bounds 중심(centroid)에서 요청 1건당 1회만 조회해 뷰포트 전체 마커에 공통 적용한다.
        double centerLat = (south + north) / 2;
        double centerLng = (west + east) / 2;
        Double weatherComfort = weatherComfort(centerLat, centerLng);
        return placeRepository.findInBoundsScored(west, south, east, north, name(category), limit).stream()
                .map(v -> PlaceResponse.of(v, null, weatherComfort))
                .toList();
    }

    public PlaceResponse getById(long id) {
        ScoredPlaceView v = placeRepository.findByIdScored(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "place not found: " + id));
        // 단건은 이 장소 좌표 기준 1회 조회(어차피 장소가 하나라 N+1 문제 없음).
        // AI 한줄 요약도 단건 상세에서만 조회한다(ADR-0010, 목록/반경/bounds는 비용 방어를 위해 미조회).
        Double weatherComfort = weatherComfort(v.getLat(), v.getLng());
        String aiSummary = aiSummaryService.summarize(id).orElse(null);
        return PlaceResponse.of(v, null, weatherComfort, aiSummary, gradedFeatures(id));
    }

    /**
     * 장소 시설 속성을 등급화(FeatureGrade)해 상세 응답용 목록으로 만든다(ADR-0005 §④).
     * present=false(부재 — 예: outlet=false)는 칩으로 그리지 않으므로 제외한다.
     */
    private List<PlaceFeatureResponse> gradedFeatures(long placeId) {
        return placeFeatureRepository.findByPlaceIdOrderByFeatureType(placeId).stream()
                .map(f -> {
                    FeatureGrade grade = FeatureGrade.of(f.getFeatureType(), f.getValue());
                    return grade.present() ? PlaceFeatureResponse.of(f, grade) : null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** 날씨 조회 실패(키 미설정·장애 등)는 null — 호출부는 comfort 성분 제외로 폴백한다(graceful degradation). */
    private Double weatherComfort(double lat, double lng) {
        return weatherService.getComfortScore(lat, lng).orElse(null);
    }

    /**
     * PostGIS 공간쿼리(반경 ST_DWithin·kNN &lt;-&gt;) 실행 구간만 Timer로 감싼다 — 애플리케이션 레이어
     * 매핑/날씨 호출 시간은 섞지 않아 "인덱스가 실제로 빠른가"를 latency로 직접 관측할 수 있다(ADR-0012의
     * EXPLAIN 정적 분석을 런타임 지표로 보완). 예외가 나도 타이머는 반드시 기록(finally)해 실패 latency도 남는다.
     */
    private <T> T timed(String metricName, PlaceCategory category, Supplier<T> query) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return query.get();
        } finally {
            String categoryTag = category == null ? "ALL" : category.name();
            sample.stop(meterRegistry.timer(metricName, "category", categoryTag));
        }
    }

    private static String name(PlaceCategory category) {
        return category == null ? null : category.name();
    }
}
