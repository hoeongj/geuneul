package com.geuneul.domain.place.dto;

import com.geuneul.domain.place.SurvivalScore;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * survival_score 응답 — 종합 점수 + 등급(마커 3색) + 성분 분해(투명성/디버깅용).
 * 스코어드 검색(반경/bounds/단건)에서만 채워지고, nearest/urgent 경로에서는 null.
 */
@Schema(description = "지금 갈만함 점수(survival_score) — 등급으로 마커 색을 칠한다")
public record SurvivalScoreResponse(
        @Schema(description = "종합 점수 0~100", example = "72") int score,
        @Schema(description = "등급: GOOD(초록·지금 좋음) | OKAY(노랑·보통) | UNKNOWN(회색·정보 부족)", example = "GOOD")
        String grade,
        @Schema(description = "거리 점수 0~1. 반경 검색에서만 non-null", example = "0.7", nullable = true)
        Double distanceScore,
        @Schema(description = "편의 점수 0~1(긍정 제보 가중 + 날씨 comfort 보정 additive, ADR-0009)", example = "0.8") double comfortScore,
        @Schema(description = "최근성 점수 0~1", example = "1.0") double freshnessScore,
        @Schema(description = "리스크 점수 0~1(부정 제보 가중)", example = "0.0") double riskScore,
        @Schema(description = "집계에 쓰인 유효(미만료) 제보 수", example = "3") long reportCount
) {

    public static SurvivalScoreResponse of(SurvivalScore s) {
        return new SurvivalScoreResponse(
                s.score(),
                s.grade().name(),
                s.distanceScore(),
                s.comfortScore(),
                s.freshnessScore(),
                s.riskScore(),
                s.reportCount()
        );
    }
}
