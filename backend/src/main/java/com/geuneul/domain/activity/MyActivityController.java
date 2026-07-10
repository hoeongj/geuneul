package com.geuneul.domain.activity;

import com.geuneul.domain.activity.dto.MyCommentResponse;
import com.geuneul.domain.activity.dto.MyReactionResponse;
import com.geuneul.domain.activity.dto.MyReviewResponse;
import com.geuneul.domain.auth.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "내 글 관리"(N6) — 로그인 유저의 내 후기/댓글/유용해요 목록. 모두 인증 필요(SecurityConfig에서 /me/reviews·
 * /me/comments·/me/reactions를 authenticated로 명시). principal.userId()로만 조회 — 남의 활동은 볼 수 없다
 * (작성자 공개 프로필은 N7 /users/{id}에서 별도 제공).
 */
@Tag(name = "MyActivity", description = "내 글 관리 — 내 후기·댓글·유용해요")
@RestController
public class MyActivityController {

    private final MyActivityService myActivityService;

    public MyActivityController(MyActivityService myActivityService) {
        this.myActivityService = myActivityService;
    }

    @Operation(summary = "내 후기 목록", description = "로그인 유저가 쓴 후기 최신순(장소명 포함).")
    @GetMapping("/me/reviews")
    public List<MyReviewResponse> myReviews(@AuthenticationPrincipal JwtService.AuthPrincipal principal) {
        return myActivityService.myReviews(principal.userId());
    }

    @Operation(summary = "내 댓글 목록", description = "로그인 유저가 쓴 후기 댓글 최신순(장소명 포함).")
    @GetMapping("/me/comments")
    public List<MyCommentResponse> myComments(@AuthenticationPrincipal JwtService.AuthPrincipal principal) {
        return myActivityService.myComments(principal.userId());
    }

    @Operation(summary = "내 유용해요 목록", description = "로그인 유저가 유용해요를 누른 후기 최신순(장소명 포함).")
    @GetMapping("/me/reactions")
    public List<MyReactionResponse> myReactions(@AuthenticationPrincipal JwtService.AuthPrincipal principal) {
        return myActivityService.myReactions(principal.userId());
    }
}
