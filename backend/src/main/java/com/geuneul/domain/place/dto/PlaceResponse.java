package com.geuneul.domain.place.dto;

import com.geuneul.domain.place.Place;
import com.geuneul.domain.place.PlaceCategory;
import com.geuneul.domain.place.PlaceDistanceView;
import com.geuneul.domain.place.ScoredPlaceView;
import com.geuneul.domain.place.SurvivalScore;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 장소 응답 DTO. JTS Point를 직접 직렬화하지 않고 lat/lng로 평탄화한다(JTS: X=lng, Y=lat).
 * distanceM은 반경/최근접 검색에서만 채워진다(중심점이 있어야 의미가 있으므로).
 * survival은 스코어드 검색(반경/bounds/단건)에서만 채워지고, nearest/urgent에서는 null.
 * aiSummary는 단건 상세(GET /places/{id})에서만 채워진다(P3 곁다리, AI는 상세 조회 비용만 진다).
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
        SurvivalScoreResponse survival,
        @Schema(description = "최근 제보 기준 AI 한줄 요약(곁다리). 단건 상세에서만 제공, 유효 제보 없거나 AI 실패 시 null",
                example = "최근 제보 기준 시원하지만 화장실은 별로예요", nullable = true)
        String aiSummary,
        @Schema(description = "등급화된 시설 속성(콘센트·wifi·소음 등). 단건 상세에서만 제공, 없으면 빈 배열", nullable = true)
        List<PlaceFeatureResponse> features
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
                null,
                null,
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
                null,
                null,
                null
        );
    }

    /**
     * 스코어드 투영 매핑 — survival_score 조립까지 포함. 날씨 신호 없음(기존 동작과 동일, 폴백).
     * @param radiusM 거리 정규화 기준 반경(반경 검색). bounds/단건은 null(거리 성분 제외).
     */
    public static PlaceResponse of(ScoredPlaceView v, Double radiusM) {
        return of(v, radiusM, null);
    }

    /**
     * 스코어드 투영 매핑 — survival_score 조립 + 날씨 comfort 보정(ADR-0009). aiSummary는 없음(목록/반경/bounds
     * 경로 — AI는 단건 상세에서만 조회한다, ADR-0010).
     * @param radiusM 거리 정규화 기준 반경(반경 검색). bounds/단건은 null(거리 성분 제외).
     * @param weatherComfort 요청(쿼리) 1건당 1회 조회한 날씨 comfort 보정[0,1](WeatherService.getComfortScore).
     *                       null이면 날씨 신호 없음 — 제보 comfort만으로 폴백.
     */
    public static PlaceResponse of(ScoredPlaceView v, Double radiusM, Double weatherComfort) {
        return of(v, radiusM, weatherComfort, null, null);
    }

    /**
     * 단건 상세 전용 투영 매핑 — survival_score 조립 + 날씨 comfort 보정 + AI 한줄 요약(ADR-0010) +
     * 등급화된 시설 속성(ADR-0005 §④, features).
     * @param radiusM 거리 정규화 기준 반경. 단건 상세는 항상 null(거리 성분 제외).
     * @param weatherComfort 이 장소 좌표 기준 1회 조회한 날씨 comfort 보정[0,1]. null이면 날씨 신호 없음.
     * @param aiSummary 최근 제보 기준 AI 한줄 요약. 유효 제보 없음·AI 미설정·실패 시 null(graceful degradation).
     * @param features 등급화된 시설 속성 목록(present=false 제외). 목록/반경/bounds 경로는 null(상세 전용).
     */
    public static PlaceResponse of(ScoredPlaceView v, Double radiusM, Double weatherComfort, String aiSummary,
                                   List<PlaceFeatureResponse> features) {
        PlaceCategory category = PlaceCategory.valueOf(v.getCategory());
        Double distanceM = v.getDistanceM() == null ? null : Math.round(v.getDistanceM() * 10) / 10.0;
        SurvivalScore score = SurvivalScore.of(
                v.getDistanceM(), radiusM, v.getReportCount(),
                v.getFreshnessScore(), v.getComfortScore(), v.getRiskScore(),
                weatherComfort, v.getFeatureComfortScore());
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
                SurvivalScoreResponse.of(score),
                aiSummary,
                features
        );
    }
}
