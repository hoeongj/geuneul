package com.geuneul.domain.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 장소 시설 속성(place_features) 읽기 엔티티 — 스키마 소유권은 Flyway V2, JPA는 검증만.
 * 쓰기는 인제스천의 벌크 upsert(PlaceBulkUpsertRepository)가 담당하고, 여기서는 상세 화면 노출용 조회만 한다.
 *
 * <p>feature_type: air_conditioned/outlet/wifi/restroom/water/seating/no_eyes/study_ok/quiet/noise_level 등.
 * value는 자유 문자열(불리언 "true"/"false" 또는 등급 "few/some/many" 등) — 등급 해석은 {@link FeatureGrade}.
 */
@Entity
@Table(name = "place_features")
public class PlaceFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "feature_type", nullable = false, length = 32)
    private String featureType;

    @Column(length = 64)
    private String value;

    @Column(length = 64)
    private String source;

    private Double confidence;

    protected PlaceFeature() {
    }

    public Long getId() {
        return id;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public String getFeatureType() {
        return featureType;
    }

    public String getValue() {
        return value;
    }

    public String getSource() {
        return source;
    }

    public Double getConfidence() {
        return confidence;
    }
}
