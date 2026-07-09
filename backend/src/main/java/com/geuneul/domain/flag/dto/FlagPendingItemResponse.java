package com.geuneul.domain.flag.dto;

import com.geuneul.domain.flag.Flag;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 관리자 검수 큐 항목 — 신고 자체 + 대상 요약(targetSummary)을 함께 담는다. 관리자가 큐 화면에서
 * 원본 제보/후기를 별도 조회하지 않고 바로 판단할 수 있도록(FlagService가 report/review를 조회해 조립).
 * 대상이 이미 삭제됐으면 targetSummary가 null이고 targetExists=false.
 */
@Schema(description = "관리자 검수 큐 항목(신고 + 대상 요약)")
public record FlagPendingItemResponse(
        @Schema(description = "신고 ID", example = "1") Long id,
        @Schema(description = "신고 대상 종류", example = "REPORT") String targetType,
        @Schema(description = "신고 대상 ID", example = "1") Long targetId,
        @Schema(description = "신고 사유", example = "FALSE_INFO") String reason,
        @Schema(description = "자유 텍스트", nullable = true) String detail,
        @Schema(description = "처리 상태", example = "PENDING") String status,
        @Schema(description = "접수 시각") OffsetDateTime createdAt,
        @Schema(description = "신고자 ID", example = "10") Long reporterId,
        @Schema(description = "대상이 아직 존재하는지(삭제됐으면 false)") boolean targetExists,
        @Schema(description = "대상 요약(장소ID·타입/별점·코멘트 일부) — 대상이 없으면 null", nullable = true)
        String targetSummary
) {
    public static FlagPendingItemResponse of(Flag f, boolean targetExists, String targetSummary) {
        return new FlagPendingItemResponse(
                f.getId(),
                f.getTargetType().name(),
                f.getTargetId(),
                f.getReason().name(),
                f.getDetail(),
                f.getStatus().name(),
                f.getCreatedAt(),
                f.getReporterId(),
                targetExists,
                targetSummary
        );
    }
}
