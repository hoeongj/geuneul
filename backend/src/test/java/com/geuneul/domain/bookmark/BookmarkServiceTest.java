package com.geuneul.domain.bookmark;

import com.geuneul.domain.bookmark.dto.BookmarkToggleResponse;
import com.geuneul.domain.place.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BookmarkService 오케스트레이션 단위테스트 — DB 없이 로컬에서 항상 돈다(TS-009 무관).
 * 실 조인 쿼리·시각(TS-016)은 BookmarkFlowIT(CI)가 커버; 여기는 upsert 분기·404·멱등만.
 */
class BookmarkServiceTest {

    private BookmarkRepository bookmarkRepository;
    private PlaceRepository placeRepository;
    private BookmarkService service;

    @BeforeEach
    void setUp() {
        bookmarkRepository = mock(BookmarkRepository.class);
        placeRepository = mock(PlaceRepository.class);
        service = new BookmarkService(bookmarkRepository, placeRepository);
    }

    @Test
    @DisplayName("신규 저장은 save를 부른다")
    void addNewSaves() {
        when(placeRepository.existsById(185L)).thenReturn(true);
        when(bookmarkRepository.findByUserIdAndPlaceId(10L, 185L)).thenReturn(Optional.empty());

        BookmarkToggleResponse res = service.add(10L, 185L, "메모");

        assertThat(res.bookmarked()).isTrue();
        verify(bookmarkRepository).save(any(Bookmark.class));
    }

    @Test
    @DisplayName("이미 저장된 장소 재저장은 save 없이 memo만 갱신(upsert)")
    void addExistingUpdatesMemo() {
        Bookmark existing = Bookmark.of(10L, 185L, "옛 메모");
        when(placeRepository.existsById(185L)).thenReturn(true);
        when(bookmarkRepository.findByUserIdAndPlaceId(10L, 185L)).thenReturn(Optional.of(existing));

        service.add(10L, 185L, "새 메모");

        assertThat(existing.getMemo()).isEqualTo("새 메모");
        verify(bookmarkRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 장소 저장은 404")
    void addMissingPlaceThrows404() {
        when(placeRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.add(10L, 999L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(bookmarkRepository, never()).save(any());
    }

    @Test
    @DisplayName("해제는 bookmarked=false를 돌려준다(없어도 멱등)")
    void removeReturnsFalse() {
        BookmarkToggleResponse res = service.remove(10L, 185L);

        assertThat(res.bookmarked()).isFalse();
        verify(bookmarkRepository).deleteByUserIdAndPlaceId(10L, 185L);
    }
}
