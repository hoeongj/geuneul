package com.geuneul.domain.notification.dto;

import com.geuneul.domain.notification.NotificationRuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 규칙 생성 요청 — type별 필드가 다르다(SURGE_NEARBY는 lat/lng/radiusM 필수).")
public record NotificationRuleRequest(
        @Schema(description = "규칙 종류", example = "SURGE_NEARBY")
        @NotNull(message = "type은 필수입니다")
        NotificationRuleType type,

        @Schema(description = "중심 위도(SURGE_NEARBY/HEAT_ESCAPE)", nullable = true) Double lat,
        @Schema(description = "중심 경도(SURGE_NEARBY/HEAT_ESCAPE)", nullable = true) Double lng,
        @Schema(description = "반경 m(SURGE_NEARBY)", example = "1500", nullable = true) Integer radiusM
) {
}
