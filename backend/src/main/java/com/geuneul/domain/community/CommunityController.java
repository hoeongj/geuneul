package com.geuneul.domain.community;

import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.community.dto.ReactionRequest;
import com.geuneul.domain.community.dto.ReactionResponse;
import com.geuneul.domain.community.dto.ReviewCommentRequest;
import com.geuneul.domain.community.dto.ReviewCommentResponse;
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
 * 후기 커뮤니티 API(2차·살, CLAUDE.md §8) — 후기 댓글 + 리액션("유용했어요").
 * 작성/리액션은 로그인 필요(SecurityConfig가 인증 요구), 댓글 조회는 공개.
 * survival_score(간판)와 완전히 분리 — 커뮤니티가 주인공이 되지 않게 최소 표면만 연다(§0-9).
 */
@Tag(name = "Community", description = "후기 댓글 · 리액션(유용했어요) — 2차 커뮤니티(살)")
@RestController
public class CommunityController {

    private final ReviewCommentService commentService;
    private final ReactionService reactionService;

    public CommunityController(ReviewCommentService commentService, ReactionService reactionService) {
        this.commentService = commentService;
        this.reactionService = reactionService;
    }

    @Operation(summary = "후기 댓글 작성 (로그인 필요)")
    @PostMapping("/reviews/{reviewId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewCommentResponse addComment(@AuthenticationPrincipal JwtService.AuthPrincipal principal,
                                            @PathVariable long reviewId,
                                            @Valid @RequestBody ReviewCommentRequest request) {
        return commentService.create(reviewId, principal.userId(), request.comment());
    }

    @Operation(summary = "후기 댓글 목록 (공개)", description = "오래된 순(대화 흐름).")
    @GetMapping("/reviews/{reviewId}/comments")
    public List<ReviewCommentResponse> listComments(@PathVariable long reviewId) {
        return commentService.listByReview(reviewId);
    }

    @Operation(summary = "리액션 추가 (로그인 필요)", description = "멱등 — 이미 남겼으면 상태 유지. 기본 HELPFUL.")
    @PostMapping("/reactions")
    public ReactionResponse react(@AuthenticationPrincipal JwtService.AuthPrincipal principal,
                                  @Valid @RequestBody ReactionRequest request) {
        return reactionService.add(request.targetType(), request.targetId(), principal.userId(),
                request.typeOrDefault());
    }

    @Operation(summary = "리액션 취소 (로그인 필요)", description = "없으면 no-op.")
    @DeleteMapping("/reactions")
    public ReactionResponse unreact(@AuthenticationPrincipal JwtService.AuthPrincipal principal,
                                    @Valid @RequestBody ReactionRequest request) {
        return reactionService.remove(request.targetType(), request.targetId(), principal.userId(),
                request.typeOrDefault());
    }
}
