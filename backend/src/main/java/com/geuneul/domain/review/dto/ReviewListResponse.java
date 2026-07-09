package com.geuneul.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/** 장소 후기 목록(페이지네이션) 응답. Page<T>를 직접 반환하지 않고 안정된 계약으로 감싼다. */
@Schema(description = "후기 목록(페이지네이션)")
public record ReviewListResponse(
        @Schema(description = "후기 목록(최신순)") List<ReviewResponse> reviews,
        @Schema(description = "현재 페이지(0-base)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "20") int size,
        @Schema(description = "전체 후기 수", example = "3") long totalElements,
        @Schema(description = "다음 페이지 존재 여부") boolean hasNext
) {

    public static ReviewListResponse of(Page<ReviewResponse> page) {
        return new ReviewListResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.hasNext());
    }
}
