package com.geuneul.domain.report.dto;

import com.geuneul.domain.report.Report;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 제보 응답. 시각은 ISO-8601(오프셋 포함) — 상대 시간("10분 전")은 클라이언트가 계산한다.
 */
@Schema(description = "제보")
public record ReportResponse(
        @Schema(description = "제보 ID", example = "1") Long id,
        @Schema(description = "장소 ID", example = "1") Long placeId,
        @Schema(description = "제보 타입", example = "COOL") String reportType,
        @Schema(description = "제보 타입 한글명", example = "시원해요") String reportTypeLabel,
        @Schema(description = "한 줄 코멘트", example = "에어컨 빵빵해요", nullable = true) String comment,
        @Schema(description = "사진 URL", nullable = true) String photoUrl,
        @Schema(description = "익명 여부", example = "true") boolean anonymous,
        @Schema(description = "GPS 방문 인증 여부 — 제보자가 장소 100m 이내에서 올린 제보", example = "true") boolean verified,
        @Schema(description = "제보 시각") OffsetDateTime createdAt,
        @Schema(description = "만료 시각 — 지나면 조회에서 제외") OffsetDateTime expiresAt
) {

    public static ReportResponse of(Report r) {
        return new ReportResponse(
                r.getId(),
                r.getPlaceId(),
                r.getReportType().name(),
                r.getReportType().label(),
                r.getComment(),
                r.getPhotoUrl(),
                r.isAnonymous(),
                r.isVerified(),
                r.getCreatedAt(),
                r.getExpiresAt()
        );
    }
}
