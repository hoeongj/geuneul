package com.geuneul.domain.community.dto;

import com.geuneul.domain.community.ReactionTarget;
import com.geuneul.domain.community.ReactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "리액션 요청(추가/삭제 공통)")
public record ReactionRequest(
        @Schema(description = "대상 종류", example = "REVIEW")
        @NotNull(message = "targetType은 필수입니다")
        ReactionTarget targetType,

        @Schema(description = "대상 ID", example = "12")
        @NotNull(message = "targetId는 필수입니다")
        Long targetId,

        @Schema(description = "리액션 종류(기본 HELPFUL)", example = "HELPFUL", nullable = true)
        ReactionType type
) {
    public ReactionType typeOrDefault() {
        return type == null ? ReactionType.HELPFUL : type;
    }
}
