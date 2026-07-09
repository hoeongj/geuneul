package com.geuneul.domain.place;

import com.geuneul.domain.ai.AiSummaryService;
import com.geuneul.domain.place.dto.PlaceResponse;
import com.geuneul.domain.weather.WeatherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class PlaceSearchService {

    private final PlaceRepository placeRepository;
    private final WeatherService weatherService;
    private final AiSummaryService aiSummaryService;

    public PlaceSearchService(PlaceRepository placeRepository, WeatherService weatherService,
                              AiSummaryService aiSummaryService) {
        this.placeRepository = placeRepository;
        this.weatherService = weatherService;
        this.aiSummaryService = aiSummaryService;
    }

    public List<PlaceResponse> searchRadius(double lat, double lng, double radiusMeters,
                                            PlaceCategory category, int limit) {
        // 반경 검색은 중심점이 있어 거리 성분(0.25)을 포함한 full survival_score를 조립한다.
        Double weatherComfort = weatherComfort(lat, lng); // 요청 1건당 1회(N+1 금지, ADR-0009)
        return placeRepository.findWithinRadiusScored(lat, lng, radiusMeters, name(category), limit).stream()
                .map(v -> PlaceResponse.of(v, radiusMeters, weatherComfort))
                .toList();
    }

    public List<PlaceResponse> searchNearest(double lat, double lng, PlaceCategory category, int limit) {
        // "화장실 급할 때" 팬아웃 — 점수 없이 거리만(마커 색을 쓰지 않는 경로). survival 미계산이라 날씨도 불필요.
        return placeRepository.findNearest(lat, lng, name(category), limit).stream()
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
        return PlaceResponse.of(v, null, weatherComfort, aiSummary);
    }

    /** 날씨 조회 실패(키 미설정·장애 등)는 null — 호출부는 comfort 성분 제외로 폴백한다(graceful degradation). */
    private Double weatherComfort(double lat, double lng) {
        return weatherService.getComfortScore(lat, lng).orElse(null);
    }

    private static String name(PlaceCategory category) {
        return category == null ? null : category.name();
    }
}
