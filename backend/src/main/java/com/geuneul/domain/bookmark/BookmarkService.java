package com.geuneul.domain.bookmark;

import com.geuneul.domain.bookmark.dto.BookmarkResponse;
import com.geuneul.domain.bookmark.dto.BookmarkToggleResponse;
import com.geuneul.domain.place.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 관심 장소(bookmark) 서비스 (A7) — 저장/해제/목록. 로그인 유저 개인화(살)라 survival_score와 무연결.
 * 저장은 upsert(이미 있으면 memo 갱신) — uq_bookmarks(V14)와 정합하며 Review의 장소당 1건 정책과 동형.
 */
@Service
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PlaceRepository placeRepository;

    public BookmarkService(BookmarkRepository bookmarkRepository, PlaceRepository placeRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.placeRepository = placeRepository;
    }

    /** 저장(멱등 upsert). 없는 장소는 404(FK 위반 500 대신 명확한 신호). */
    @Transactional
    public BookmarkToggleResponse add(long userId, long placeId, String memo) {
        if (!placeRepository.existsById(placeId)) {
            throw new ResponseStatusException(NOT_FOUND, "place not found: " + placeId);
        }
        bookmarkRepository.findByUserIdAndPlaceId(userId, placeId)
                .ifPresentOrElse(
                        b -> b.updateMemo(memo), // 재저장 = memo 갱신(더티체킹으로 flush)
                        () -> bookmarkRepository.save(Bookmark.of(userId, placeId, memo)));
        return BookmarkToggleResponse.of(placeId, true);
    }

    /** 해제(없으면 no-op — 멱등). */
    @Transactional
    public BookmarkToggleResponse remove(long userId, long placeId) {
        bookmarkRepository.deleteByUserIdAndPlaceId(userId, placeId);
        return BookmarkToggleResponse.of(placeId, false);
    }

    /** 마이페이지 목록 — 저장 최신순, soft-delete된 장소 제외. */
    @Transactional(readOnly = true)
    public List<BookmarkResponse> list(long userId) {
        return bookmarkRepository.findBookmarksWithPlace(userId).stream()
                .map(BookmarkResponse::of)
                .toList();
    }
}
