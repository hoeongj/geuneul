package com.geuneul.domain.place;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 장소 시설 속성 조회 리포지토리 — 상세 화면(GET /places/{id})에서 시설 칩을 그리기 위한 읽기 전용.
 * uq_place_features(place_id, feature_type) 유니크라 장소당 타입별 1건.
 */
public interface PlaceFeatureRepository extends JpaRepository<PlaceFeature, Long> {

    List<PlaceFeature> findByPlaceIdOrderByFeatureType(long placeId);
}
