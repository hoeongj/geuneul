package com.geuneul.domain.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "후기 댓글 작성 요청")
public record ReviewCommentRequest(
        @Schema(description = "댓글 내용(최대 500자)", example = "여기 진짜 시원해요!")
        @NotBlank(message = "댓글 내용은 필수입니다")
        @Size(max = 500, message = "댓글은 500자 이하여야 합니다")
        String comment
) {
}
