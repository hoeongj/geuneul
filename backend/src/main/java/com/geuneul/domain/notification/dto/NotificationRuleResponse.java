package com.geuneul.domain.notification.dto;

import com.geuneul.domain.notification.NotificationRule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "알림 규칙")
public record NotificationRuleResponse(
        @Schema(description = "규칙 ID") long id,
        @Schema(description = "종류", example = "SURGE_NEARBY") String type,
        @Schema(description = "중심 위도", nullable = true) Double centerLat,
        @Schema(description = "중심 경도", nullable = true) Double centerLng,
        @Schema(description = "반경 m", nullable = true) Integer radiusM,
        @Schema(description = "활성 여부") boolean active,
        @Schema(description = "생성 시각") OffsetDateTime createdAt
) {

    public static NotificationRuleResponse of(NotificationRule r) {
        return new NotificationRuleResponse(r.getId(), r.getType().name(), r.getCenterLat(), r.getCenterLng(),
                r.getRadiusM(), r.isActive(), r.getCreatedAt());
    }
}
