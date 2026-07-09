package com.geuneul.domain.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 사진 업로드 presign 응답. 브라우저는 uploadUrl로 파일을 직접 PUT하고(백엔드는 파일을 거치지 않음),
 * 성공하면 objectUrl을 report/review 제출 바디(photoUrl / photos[])에 그대로 첨부한다.
 */
@Schema(description = "사진 업로드 presign 응답")
public record PhotoPresignResponse(
        @Schema(description = "브라우저가 PUT할 presigned URL — 짧은 시간 내(2분) 만료") String uploadUrl,
        @Schema(description = "업로드 완료 후 report/review 바디에 넣을 최종 오브젝트 URL") String objectUrl,
        @Schema(description = "S3 오브젝트 키") String key,
        @Schema(description = "presigned URL 만료 시각") OffsetDateTime expiresAt
) {
}
