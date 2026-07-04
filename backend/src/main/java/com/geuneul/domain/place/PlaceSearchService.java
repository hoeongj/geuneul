package com.geuneul.domain.place;

import com.geuneul.domain.place.dto.PlaceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class PlaceSearchService {

    private final PlaceRepository placeRepository;

    public PlaceSearchService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    public List<PlaceResponse> searchRadius(double lat, double lng, double radiusMeters,
                                            PlaceCategory category, int limit) {
        // 반경 검색은 중심점이 있어 거리 성분(0.25)을 포함한 full survival_score를 조립한다.
        return placeRepository.findWithinRadiusScored(lat, lng, radiusMeters, name(category), limit).stream()
                .map(v -> PlaceResponse.of(v, radiusMeters))
                .toList();
    }

    public List<PlaceResponse> searchNearest(double lat, double lng, PlaceCategory category, int limit) {
        // "화장실 급할 때" 팬아웃 — 점수 없이 거리만(마커 색을 쓰지 않는 경로).
        return placeRepository.findNearest(lat, lng, name(category), limit).stream()
                .map(PlaceResponse::of)
                .toList();
    }

    public List<PlaceResponse> searchBounds(double west, double south, double east, double north,
                                            PlaceCategory category, int limit) {
        // 마커: 중심점이 없어 거리 성분을 빼고 "장소 자체 상태"로 점수/등급을 낸다(radiusM=null).
        return placeRepository.findInBoundsScored(west, south, east, north, name(category), limit).stream()
                .map(v -> PlaceResponse.of(v, null))
                .toList();
    }

    public PlaceResponse getById(long id) {
        return placeRepository.findByIdScored(id)
                .map(v -> PlaceResponse.of(v, null))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "place not found: " + id));
    }

    private static String name(PlaceCategory category) {
        return category == null ? null : category.name();
    }
}
