package com.geuneul.domain.report.dto;

import com.geuneul.domain.report.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 제보 생성 요청. MVP는 익명(비로그인) — 사진(P2 presign)·로그인 신뢰도는 이후 추가.
 * anonymous 미지정(null)이면 true(익명) — 현재 인증이 없으므로 사실상 항상 익명.
 */
@Schema(description = "제보 생성 요청")
public record ReportCreateRequest(
        @Schema(description = "장소 ID", example = "1")
        @NotNull(message = "placeId는 필수입니다")
        Long placeId,

        @Schema(description = "제보 타입", example = "COOL")
        @NotNull(message = "reportType은 필수입니다")
        ReportType reportType,

        @Schema(description = "한 줄 코멘트 (선택, 최대 120자)", example = "에어컨 빵빵해요")
        @Size(max = 120, message = "코멘트는 120자 이하여야 합니다")
        String comment,

        @Schema(description = "익명 여부 (기본 true)", example = "true", nullable = true)
        Boolean anonymous
) {
    public boolean anonymousOrDefault() {
        return anonymous == null || anonymous;
    }
}
