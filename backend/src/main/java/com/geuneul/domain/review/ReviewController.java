package com.geuneul.domain.review;

import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.review.dto.ReviewCreateRequest;
import com.geuneul.domain.review.dto.ReviewListResponse;
import com.geuneul.domain.review.dto.ReviewResponse;
import com.geuneul.global.web.ApiRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 영구 후기(review) API (CLAUDE.md §9). survival_score(휘발성 제보, §5)와 완전히 분리된
 * 장소 평판 콘텐츠 — 작성은 로그인 필요(JWT, 익명 불가). SecurityConfig가 POST /reviews를
 * 인증 요구로 보호하므로, 여기 도달했다면 principal은 항상 유효하다(AuthController.me()와 동일 패턴).
 * 조회(GET /places/{id}/reviews)는 공개.
 */
@Tag(name = "Reviews", description = "영구 평판 후기 — 로그인 필요, 장소당 1건(재작성 시 갱신)")
@RestController
public class ReviewController {

    static final int MAX_PAGE_SIZE = 50;

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "후기 작성/수정 (로그인 필요)",
            description = "장소당 1건 — 이미 작성한 후기가 있으면 내용을 갱신한다(구글맵 관행). 비로그인은 401.")
    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(@AuthenticationPrincipal JwtService.AuthPrincipal principal,
                                 @Valid @RequestBody ReviewCreateRequest request) {
        return reviewService.create(principal.userId(), request);
    }

    @Operation(summary = "장소의 후기 목록 (공개)",
            description = "최신순 페이지네이션. page 기본 0, size 기본 20(최대 50).")
    @GetMapping("/places/{placeId}/reviews")
    public ReviewListResponse list(@PathVariable long placeId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return reviewService.listByPlace(placeId, Math.max(page, 0), ApiRequests.clampLimit(size, MAX_PAGE_SIZE));
    }
}
