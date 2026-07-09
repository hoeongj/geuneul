package com.geuneul.domain.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 사진 업로드 presign 요청. contentLength를 요구해 그대로 서명에 실어(PhotoService) 브라우저가
 * 다른 크기로 PUT하면 S3가 서명 불일치로 거부하게 만든다 — presigned PUT에서 크기 상한을 강제하는
 * 표준 SigV4 기법(서명된 헤더는 실제 요청과 정확히 일치해야 함). content-length-range 조건을 쓰는
 * presigned POST 방식도 있으나, PUT 한 번으로 끝나는 단순함을 택했다(WORKLOG 근거).
 */
@Schema(description = "사진 업로드 presign 요청")
public record PhotoPresignRequest(
        @Schema(description = "MIME 타입 — image/jpeg, image/png, image/webp만 허용", example = "image/jpeg")
        @NotBlank(message = "contentType은 필수입니다")
        String contentType,

        @Schema(description = "업로드할 파일 크기(byte). 최대 8MB", example = "2500000")
        @NotNull(message = "contentLength는 필수입니다")
        @Positive(message = "contentLength는 양수여야 합니다")
        Long contentLength,

        @Schema(description = "용도: report(익명 가능, 기본값) | review(로그인 필요)", example = "report", nullable = true)
        String purpose
) {
}
