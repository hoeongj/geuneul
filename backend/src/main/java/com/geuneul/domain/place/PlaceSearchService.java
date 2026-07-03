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
        return placeRepository.findWithinRadius(lat, lng, radiusMeters, name(category), limit).stream()
                .map(PlaceResponse::of)
                .toList();
    }

    public List<PlaceResponse> searchNearest(double lat, double lng, PlaceCategory category, int limit) {
        return placeRepository.findNearest(lat, lng, name(category), limit).stream()
                .map(PlaceResponse::of)
                .toList();
    }

    public List<PlaceResponse> searchBounds(double west, double south, double east, double north,
                                            PlaceCategory category, int limit) {
        return placeRepository.findInBounds(west, south, east, north, name(category), limit).stream()
                .map(p -> PlaceResponse.of(p, null))
                .toList();
    }

    public PlaceResponse getById(long id) {
        Place place = placeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "place not found: " + id));
        return PlaceResponse.of(place, null);
    }

    private static String name(PlaceCategory category) {
        return category == null ? null : category.name();
    }
}
