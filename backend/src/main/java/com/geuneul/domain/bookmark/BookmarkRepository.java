package com.geuneul.domain.bookmark;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByUserIdAndPlaceId(long userId, long placeId);

    boolean existsByUserIdAndPlaceId(long userId, long placeId);

    long deleteByUserIdAndPlaceId(long userId, long placeId);

    /**
     * 마이페이지 관심 장소 목록 — bookmark + 장소 정보 조인, 최신순. idx_bookmarks_user 경로.
     * soft-delete(폐업 회전)된 장소는 제외한다(p.deleted_at IS NULL, ADR-0006 — 검색·상세와 동일 규약).
     */
    @Query(value = """
            SELECT p.id AS "placeId", p.name AS "name", p.category AS "category", p.address AS "address",
                   ST_Y(p.geom) AS "lat", ST_X(p.geom) AS "lng", b.memo AS "memo", b.created_at AS "createdAt"
            FROM bookmarks b
            JOIN places p ON p.id = b.place_id
            WHERE b.user_id = :userId AND p.deleted_at IS NULL
            ORDER BY b.created_at DESC
            """, nativeQuery = true)
    List<BookmarkView> findBookmarksWithPlace(@Param("userId") long userId);
}
