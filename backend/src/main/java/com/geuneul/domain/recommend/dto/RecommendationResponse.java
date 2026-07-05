package com.geuneul.domain.recommend.dto;

import com.geuneul.domain.place.dto.PlaceResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 추천 결과 한 건 (ADR-0008). 장소(+§5 표준 가중치로 낸 survival 배지)에
 * 시나리오 적합도(matchScore)와 근거(reason)를 얹는다.
 *
 * <p><b>두 점수의 의미가 다르다(의도):</b> {@code place.survival}은 §5 표준 가중치로 낸
 * "이 장소의 지금 상태"이고, {@code matchScore}는 시나리오 가중치로 낸
 * "이 상황에 얼마나 맞나"(이 목록의 정렬 기준)다. (지도 배지와 같은 §5 공식을 쓰지만,
 * 거리 성분은 추천 반경 기준이라 지도 단건 상세의 배지와 점수가 다를 수 있다.)
 */
@Schema(description = "추천 결과 — 장소 + 시나리오 적합도 + 근거")
public record RecommendationResponse(
        @Schema(description = "장소(§5 표준 가중치로 낸 survival 배지 포함)") PlaceResponse place,
        @Schema(description = "시나리오 적합도 0~100 — 이 목록의 정렬 기준(거리·실시간 상태를 시나리오 가중으로 조립)",
                example = "78") int matchScore,
        @Schema(description = "추천 근거(실시간 제보 요약)", example = "최근 좋은 제보 2건") String reason
) {
}
