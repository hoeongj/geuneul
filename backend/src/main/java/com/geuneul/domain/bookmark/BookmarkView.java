package com.geuneul.domain.bookmark;

import java.time.Instant;

/**
 * 관심 장소 목록 투영(GET /me/bookmarks) — bookmark + 장소 정보 조인. 좌표는 ST_Y/ST_X로 평탄화.
 * created_at은 네이티브 프로젝션이라 Instant로 받는다(TS-016) — DTO에서 UTC 부착.
 */
public interface BookmarkView {
    long getPlaceId();

    String getName();

    String getCategory();

    String getAddress();

    double getLat();

    double getLng();

    String getMemo();

    Instant getCreatedAt();
}
