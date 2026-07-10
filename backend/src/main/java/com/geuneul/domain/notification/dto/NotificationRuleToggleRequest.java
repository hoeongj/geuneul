package com.geuneul.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 규칙 활성 토글")
public record NotificationRuleToggleRequest(
        @Schema(description = "활성 여부", example = "false")
        @NotNull(message = "active는 필수입니다")
        Boolean active
) {
}
