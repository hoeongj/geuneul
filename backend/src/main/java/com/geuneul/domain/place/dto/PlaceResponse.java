package com.geuneul.domain.place.dto;

import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceDistanceView;
import com.geuneul.domain.place.ScoredPlaceView;
import com.geuneul.domain.place.SurvivalScore;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 장소 응답 DTO. JTS Point를 직접 직렬화하지 않고 lat/lng로 평탄화한다(JTS: X=lng, Y=lat).
 * distanceM은 반경/최근접 검색에서만 채워진다(중심점이 있어야 의미가 있으므로).
 * survival은 스코어드 검색(반경/bounds/단건)에서만 채워지고, nearest/urgent에서는 null.
 */
@Schema(description = "장소")
public record PlaceResponse(
        @Schema(description = "장소 ID", example = "1") Long id,
        @Schema(description = "이름", example = "숭실대학교 중앙도서관") String name,
        @Schema(description = "카테고리", example = "LIBRARY") String category,
        @Schema(description = "카테고리 한글명", example = "도서관") String categoryLabel,
        @Schema(description = "주소", example = "서울 동작구 상도로 369") String address,
        @Schema(description = "위도", example = "37.4967") double lat,
        @Schema(description = "경도", example = "126.9575") double lng,
        @Schema(description = "데이터 출처", example = "seed") String source,
        @Schema(description = "검색 중심점으로부터의 거리(m). 반경/최근접 검색에서만 제공", example = "241.5", nullable = true)
        Double distanceM,
        @Schema(description = "지금 갈만함 점수(survival_score). 스코어드 검색에서만 제공", nullable = true)
        SurvivalScoreResponse survival
) {

    public static PlaceResponse of(Place p, Double distanceM) {
        return new PlaceResponse(
                p.getId(),
                p.getName(),
                p.getCategory().name(),
                p.getCategory().label(),
                p.getAddress(),
                p.getGeom().getY(),
                p.getGeom().getX(),
                p.getSource(),
                distanceM,
                null
        );
    }

    /** 반경/최근접 투영 매핑(점수 없음) — 거리는 DB가 계산한 값(타원체)을 소수1자리로 표기. */
    public static PlaceResponse of(PlaceDistanceView v) {
        PlaceCategory category = PlaceCategory.valueOf(v.getCategory());
        return new PlaceResponse(
                v.getId(),
                v.getName(),
                category.name(),
                category.label(),
                v.getAddress(),
                v.getLat(),
                v.getLng(),
                v.getSource(),
                Math.round(v.getDistanceM() * 10) / 10.0,
                null
        );
    }

    /**
     * 스코어드 투영 매핑 — survival_score 조립까지 포함.
     * @param radiusM 거리 정규화 기준 반경(반경 검색). bounds/단건은 null(거리 성분 제외).
     */
    public static PlaceResponse of(ScoredPlaceView v, Double radiusM) {
        PlaceCategory category = PlaceCategory.valueOf(v.getCategory());
        Double distanceM = v.getDistanceM() == null ? null : Math.round(v.getDistanceM() * 10) / 10.0;
        SurvivalScore score = SurvivalScore.of(
                v.getDistanceM(), radiusM, v.getReportCount(),
                v.getFreshnessScore(), v.getComfortScore(), v.getRiskScore());
        return new PlaceResponse(
                v.getId(),
                v.getName(),
                category.name(),
                category.label(),
                v.getAddress(),
                v.getLat(),
                v.getLng(),
                v.getSource(),
                distanceM,
                SurvivalScoreResponse.of(score)
        );
    }
}
