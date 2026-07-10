package com.geuneul.domain.follow;

import com.geuneul.domain.auth.JwtService;
import com.geuneul.domain.follow.dto.FollowResponse;
import com.geuneul.domain.follow.dto.FollowingResponse;
import com.geuneul.domain.follow.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 작성자 공개 프로필 + 커먼스 세이프 팔로우(N7, ADR-0023).
 * <ul>
 *   <li>{@code GET /users/{id}} — 공개(누구나). 로그인 시 팔로우 상태(following)를 채운다.</li>
 *   <li>{@code POST/DELETE /users/{id}/follow} — 로그인 필요(멱등). SecurityConfig에서 보호.</li>
 *   <li>{@code GET /me/following} — 로그인 필요, "나만" 보는 팔로잉 목록(팔로워 목록은 없음, §0-9).</li>
 * </ul>
 */
@Tag(name = "Follow", description = "작성자 공개 프로필 · 커먼스 세이프 팔로우")
@RestController
public class UserController {

    private final FollowService followService;

    public UserController(FollowService followService) {
        this.followService = followService;
    }

    @Operation(summary = "작성자 공개 프로필",
            description = "닉네임·신뢰도·팔로워 수(목록 아님)·공개 후기 목록. 로그인 시 following(내가 팔로우 중인지) 포함.")
    @GetMapping("/users/{id}")
    public UserProfileResponse profile(@PathVariable long id,
                                       @AuthenticationPrincipal JwtService.AuthPrincipal principal) {
        return followService.profile(id, principal == null ? null : principal.userId());
    }

    @Operation(summary = "팔로우(멱등)", description = "로그인 필요. 자기 자신·없는 유저는 거부.")
    @PostMapping("/users/{id}/follow")
    public FollowResponse follow(@PathVariable long id,
                                 @AuthenticationPrincipal JwtService.AuthPrincipal principal) {
        return followService.follow(principal.userId(), id);
    }

    @Operation(summary = "언팔로우(멱등)", description = "로그인 필요.")
    @DeleteMapping("/users/{id}/follow")
    public FollowResponse unfollow(@PathVariable long id,
                                   @AuthenticationPrincipal JwtService.AuthPrincipal principal) {
        return followService.unfollow(principal.userId(), id);
    }

    @Operation(summary = "내 팔로잉 목록", description = "로그인 필요. 나만 보는 목록 — 팔로우한 작성자 재방문용.")
    @GetMapping("/me/following")
    public List<FollowingResponse> myFollowing(@AuthenticationPrincipal JwtService.AuthPrincipal principal) {
        return followService.myFollowing(principal.userId());
    }
}
