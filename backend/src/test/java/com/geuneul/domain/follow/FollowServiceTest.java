package com.geuneul.domain.follow;

import com.geuneul.domain.auth.User;
import com.geuneul.domain.auth.UserRepository;
import com.geuneul.domain.follow.dto.FollowResponse;
import com.geuneul.domain.follow.dto.UserProfileResponse;
import com.geuneul.domain.review.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 커먼스 세이프 팔로우(N7) 서비스 로직 단위테스트 — 멱등·자기팔로우 거부·없는유저 404·프로필 조립을 Docker 없이 검증.
 * 실 UNIQUE 제약·조인은 FollowFlowIT(실 Postgres, CI)가 검증한다.
 */
class FollowServiceTest {

    private final FollowRepository followRepository = mock(FollowRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final FollowService service = new FollowService(followRepository, userRepository, reviewRepository);

    @Test
    @DisplayName("팔로우: 미팔로우 상태면 저장하고 following=true + 갱신 카운트")
    void followSaves() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(false);
        when(followRepository.countByFolloweeId(2L)).thenReturn(1L);

        FollowResponse r = service.follow(1L, 2L);

        assertThat(r.following()).isTrue();
        assertThat(r.followerCount()).isEqualTo(1L);
        verify(followRepository).save(any(Follow.class));
    }

    @Test
    @DisplayName("이미 팔로우 중이면 저장하지 않고 멱등(following=true)")
    void followIsIdempotent() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(true);
        when(followRepository.countByFolloweeId(2L)).thenReturn(1L);

        FollowResponse r = service.follow(1L, 2L);

        assertThat(r.following()).isTrue();
        verify(followRepository, never()).save(any());
    }

    @Test
    @DisplayName("자기 자신 팔로우는 400 — 저장 안 함")
    void selfFollowIsRejected() {
        assertThatThrownBy(() -> service.follow(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        verify(followRepository, never()).save(any());
    }

    @Test
    @DisplayName("없는 유저 팔로우는 404")
    void followUnknownUserIs404() {
        when(userRepository.existsById(999L)).thenReturn(false);
        assertThatThrownBy(() -> service.follow(1L, 999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        verify(followRepository, never()).save(any());
    }

    @Test
    @DisplayName("언팔로우: 삭제 후 following=false")
    void unfollowDeletes() {
        when(followRepository.countByFolloweeId(2L)).thenReturn(0L);

        FollowResponse r = service.unfollow(1L, 2L);

        assertThat(r.following()).isFalse();
        assertThat(r.followerCount()).isZero();
        verify(followRepository).deleteByFollowerIdAndFolloweeId(1L, 2L);
    }

    @Test
    @DisplayName("공개 프로필: 팔로워 수 + following(뷰어) + 후기목록을 조립한다")
    void profileAssembles() {
        User author = mock(User.class);
        when(author.getId()).thenReturn(2L);
        when(author.getNickname()).thenReturn("작성자");
        when(author.getProfileImage()).thenReturn(null);
        when(author.getTrustScore()).thenReturn(4.5);
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(followRepository.countByFolloweeId(2L)).thenReturn(3L);
        when(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(true);
        when(reviewRepository.findByUserIdWithPlace(2L)).thenReturn(List.of());

        UserProfileResponse p = service.profile(2L, 1L);

        assertThat(p.id()).isEqualTo(2L);
        assertThat(p.nickname()).isEqualTo("작성자");
        assertThat(p.trustScore()).isEqualTo(4.5);
        assertThat(p.followerCount()).isEqualTo(3L);
        assertThat(p.following()).isTrue();
        assertThat(p.reviews()).isEmpty();
    }

    @Test
    @DisplayName("비로그인 뷰어(viewerId=null)면 following=false")
    void profileForAnonymousViewer() {
        User author = mock(User.class);
        when(author.getId()).thenReturn(2L);
        when(author.getNickname()).thenReturn("작성자");
        when(author.getTrustScore()).thenReturn(0.0);
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(followRepository.countByFolloweeId(2L)).thenReturn(0L);
        when(reviewRepository.findByUserIdWithPlace(2L)).thenReturn(List.of());

        UserProfileResponse p = service.profile(2L, null);

        assertThat(p.following()).isFalse();
        verify(followRepository, never()).existsByFollowerIdAndFolloweeId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("없는 유저 프로필은 404")
    void unknownProfileIs404() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.profile(999L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
