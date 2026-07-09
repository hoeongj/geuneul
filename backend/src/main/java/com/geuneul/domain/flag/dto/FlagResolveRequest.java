package com.geuneul.domain.flag.dto;

import com.geuneul.domain.flag.FlagStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 관리자 신고 처리 요청. status는 RESOLVED|DISMISSED만 허용(PENDING 재지정은 서비스에서 400). */
@Schema(description = "신고 처리 요청 — ADMIN 전용")
public record FlagResolveRequest(
        @Schema(description = "처리 결과 상태(RESOLVED|DISMISSED)", example = "RESOLVED")
        @NotNull(message = "status는 필수입니다")
        FlagStatus status
) {
}
