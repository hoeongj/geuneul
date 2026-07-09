package com.geuneul.domain.place.dto;

import com.geuneul.domain.place.FeatureGrade;
import com.geuneul.domain.place.PlaceFeature;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 장소 시설 속성 1건의 상세 응답(ADR-0005 §④) — 등급화된 라벨/방향을 함께 싣는다.
 * 상세 화면 시설 칩용. present=false(부재)는 서비스가 애초에 목록에서 제외한다.
 */
@Schema(description = "장소 시설 속성(등급화)")
public record PlaceFeatureResponse(
        @Schema(description = "원본 feature_type", example = "outlet") String type,
        @Schema(description = "원본 value(자유 문자열)", example = "many", nullable = true) String value,
        @Schema(description = "등급 레벨", example = "MANY") String level,
        @Schema(description = "표시 라벨(등급 반영)", example = "콘센트 많음") String label,
        @Schema(description = "쾌적 방향(POSITIVE/NEGATIVE/NEUTRAL)", example = "POSITIVE") String polarity,
        @Schema(description = "출처(public/ugc 등)", example = "public", nullable = true) String source,
        @Schema(description = "신뢰도[0,1]", example = "0.6", nullable = true) Double confidence
) {

    public static PlaceFeatureResponse of(PlaceFeature f, FeatureGrade grade) {
        return new PlaceFeatureResponse(
                f.getFeatureType(), f.getValue(), grade.level(), grade.label(),
                grade.polarity().name(), f.getSource(), f.getConfidence());
    }
}
