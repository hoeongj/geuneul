package com.geuneul.domain.flag.dto;

import com.geuneul.domain.flag.Flag;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** 신고 응답(접수/처리 공통). */
@Schema(description = "신고")
public record FlagResponse(
        @Schema(description = "신고 ID", example = "1") Long id,
        @Schema(description = "신고 대상 종류", example = "REPORT") String targetType,
        @Schema(description = "신고 대상 ID", example = "1") Long targetId,
        @Schema(description = "신고 사유", example = "FALSE_INFO") String reason,
        @Schema(description = "자유 텍스트", nullable = true) String detail,
        @Schema(description = "처리 상태", example = "PENDING") String status,
        @Schema(description = "접수 시각") OffsetDateTime createdAt,
        @Schema(description = "처리 시각 — 미처리면 null", nullable = true) OffsetDateTime resolvedAt
) {
    public static FlagResponse of(Flag f) {
        return new FlagResponse(
                f.getId(),
                f.getTargetType().name(),
                f.getTargetId(),
                f.getReason().name(),
                f.getDetail(),
                f.getStatus().name(),
                f.getCreatedAt(),
                f.getResolvedAt()
        );
    }
}
