package com.geuneul.domain.bookmark;

import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.bookmark.dto.BookmarkCreateRequest;
import com.geuneul.domain.bookmark.dto.BookmarkResponse;
import com.geuneul.domain.bookmark.dto.BookmarkToggleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관심 장소(bookmark) API (A7 · ERD §8) — 전부 로그인 필요(SecurityConfig가 보호하므로 principal은 유효).
 * survival_score(간판)와 무관한 개인화(살). B1 알림의 "관심 장소 상태 변화"가 이 테이블을 재사용한다.
 */
@Tag(name = "Bookmarks", description = "관심 장소 저장/해제/목록 — 로그인 필요")
@RestController
public class BookmarkController {

    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @Operation(summary = "관심 장소 저장 (로그인 필요)", description = "멱등 — 이미 저장했으면 memo만 갱신.")
    @PostMapping("/bookmarks")
    @ResponseStatus(HttpStatus.CREATED)
    public BookmarkToggleResponse add(@AuthenticationPrincipal JwtService.AuthPrincipal principal,
                                      @Valid @RequestBody BookmarkCreateRequest request) {
        return bookmarkService.add(principal.userId(), request.placeId(), request.memo());
    }

    @Operation(summary = "관심 장소 해제 (로그인 필요)", description = "없으면 no-op(멱등).")
    @DeleteMapping("/bookmarks/{placeId}")
    public BookmarkToggleResponse remove(@AuthenticationPrincipal JwtService.AuthPrincipal principal,
                                         @PathVariable long placeId) {
        return bookmarkService.remove(principal.userId(), placeId);
    }

    @Operation(summary = "내 관심 장소 목록 (로그인 필요)", description = "저장 최신순, 폐업(soft-delete) 장소 제외.")
    @GetMapping("/me/bookmarks")
    public List<BookmarkResponse> list(@AuthenticationPrincipal JwtService.AuthPrincipal principal) {
        return bookmarkService.list(principal.userId());
    }
}
