package com.geuneul.domain.flag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/** 관리자 검수 큐 목록(페이지네이션) 응답 — ReviewListResponse와 동일한 안정 계약 패턴. */
@Schema(description = "관리자 검수 큐 목록(페이지네이션)")
public record FlagPendingListResponse(
        @Schema(description = "대기중 신고 목록(오래된 순)") List<FlagPendingItemResponse> flags,
        @Schema(description = "현재 페이지(0-base)", example = "0") int page,
        @Schema(description = "페이지 크기", example = "20") int size,
        @Schema(description = "전체 대기 신고 수", example = "3") long totalElements,
        @Schema(description = "다음 페이지 존재 여부") boolean hasNext
) {
    public static FlagPendingListResponse of(Page<FlagPendingItemResponse> page) {
        return new FlagPendingListResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.hasNext());
    }
}
