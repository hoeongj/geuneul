package com.geuneul.domain.report.dto;

import com.geuneul.domain.report.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 제보 생성 요청. MVP는 익명(비로그인) — 로그인 신뢰도는 이후 추가.
 * anonymous 미지정(null)이면 true(익명) — 현재 인증이 없으므로 사실상 항상 익명.
 * photoUrl은 POST /photos/presign이 돌려준 objectUrl을 그대로 넣는다(PhotoController, purpose=report).
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

        @Schema(description = "사진 URL (선택) — POST /photos/presign 응답의 objectUrl", nullable = true)
        @Size(max = 512, message = "사진 URL은 512자 이하여야 합니다")
        @Pattern(regexp = "^https://.*", message = "사진 URL은 https://로 시작해야 합니다")
        String photoUrl,

        @Schema(description = "익명 여부 (기본 true)", example = "true", nullable = true)
        Boolean anonymous,

        @Schema(description = "제보자 현재 위도 (선택) — 장소 100m 이내면 GPS 방문 인증(verified). lng과 함께 보낸다.",
                example = "37.4986", nullable = true)
        Double lat,

        @Schema(description = "제보자 현재 경도 (선택) — GPS 방문 인증용. lat과 함께 보낸다.",
                example = "126.9531", nullable = true)
        Double lng
) {
    public boolean anonymousOrDefault() {
        return anonymous == null || anonymous;
    }

    /** 제보자 좌표가 둘 다 있을 때만 GPS 방문 인증을 시도한다(하나만 오면 무시 — 부분 좌표는 무의미). */
    public boolean hasReporterLocation() {
        return lat != null && lng != null;
    }
}
