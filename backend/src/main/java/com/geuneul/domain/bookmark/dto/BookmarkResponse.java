package com.geuneul.domain.bookmark.dto;

import com.geuneul.domain.bookmark.BookmarkView;
import com.geuneul.domain.place.PlaceCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 관심 장소 목록 항목(GET /me/bookmarks) — 장소 요약 + memo + 저장 시각. */
@Schema(description = "관심 장소")
public record BookmarkResponse(
        @Schema(description = "장소 ID", example = "185") long placeId,
        @Schema(description = "이름", example = "노들서가") String name,
        @Schema(description = "카테고리", example = "STUDY_CAFE") String category,
        @Schema(description = "카테고리 한글명", example = "스터디카페") String categoryLabel,
        @Schema(description = "주소", example = "서울 동작구") String address,
        @Schema(description = "위도", example = "37.5124") double lat,
        @Schema(description = "경도", example = "126.9530") double lng,
        @Schema(description = "메모", nullable = true) String memo,
        @Schema(description = "저장 시각") OffsetDateTime createdAt
) {

    public static BookmarkResponse of(BookmarkView v) {
        PlaceCategory category = PlaceCategory.valueOf(v.getCategory());
        // 네이티브 프로젝션 created_at은 Instant(TS-016) → UTC 부착(앱 전역 UTC).
        return new BookmarkResponse(
                v.getPlaceId(), v.getName(), category.name(), category.label(), v.getAddress(),
                v.getLat(), v.getLng(), v.getMemo(), v.getCreatedAt().atOffset(ZoneOffset.UTC));
    }
}
