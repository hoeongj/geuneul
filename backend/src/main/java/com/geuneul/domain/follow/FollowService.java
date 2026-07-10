package com.geuneul.domain.follow;

import com.geuneul.domain.activity.dto.MyReviewResponse;
import com.geuneul.domain.auth.User;
import com.geuneul.domain.auth.UserRepository;
import com.geuneul.domain.follow.dto.FollowResponse;
import com.geuneul.domain.follow.dto.FollowingResponse;
import com.geuneul.domain.follow.dto.UserProfileResponse;
import com.geuneul.domain.review.ReviewRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 커먼스 세이프 팔로우(N7, ADR-0023). 팔로우를 소셜 그래프가 아니라 사적 북마크 + 인기 신호로 관리한다(§0-9):
 * 작성자 공개 프로필은 팔로워 "수"만 노출(목록 없음), 팔로잉은 "나만" 본다. 후기 목록은 N6과 동일 쿼리 재사용.
 */
@Service
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;

    public FollowService(FollowRepository followRepository, UserRepository userRepository,
                         ReviewRepository reviewRepository) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
    }

    /**
     * 작성자 공개 프로필. {@code viewerId}가 있으면(로그인) 팔로우 상태를 채워 버튼에 반영한다. 공개 조회.
     */
    public UserProfileResponse profile(long userId, Long viewerId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "user not found: " + userId));
        long followerCount = followRepository.countByFolloweeId(userId);
        boolean following = viewerId != null && viewerId != userId
                && followRepository.existsByFollowerIdAndFolloweeId(viewerId, userId);
        List<MyReviewResponse> reviews = reviewRepository.findByUserIdWithPlace(userId).stream()
                .map(MyReviewResponse::of).toList();
        return UserProfileResponse.of(user, followerCount, following, reviews);
    }

    /** 팔로우(멱등). 자기 자신·없는 유저는 거부. 동시 이중요청은 UNIQUE 제약으로 멱등 처리. */
    @Transactional
    public FollowResponse follow(long followerId, long followeeId) {
        if (followerId == followeeId) {
            throw new ResponseStatusException(BAD_REQUEST, "자기 자신은 팔로우할 수 없어요.");
        }
        if (!userRepository.existsById(followeeId)) {
            throw new ResponseStatusException(NOT_FOUND, "user not found: " + followeeId);
        }
        if (!followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            try {
                followRepository.save(Follow.of(followerId, followeeId));
            } catch (DataIntegrityViolationException race) {
                // 동시 이중 팔로우 — 이미 팔로우된 것으로 간주(멱등, uq_follow가 backstop).
            }
        }
        return new FollowResponse(true, followRepository.countByFolloweeId(followeeId));
    }

    /** 언팔로우(멱등) — 없으면 no-op. */
    @Transactional
    public FollowResponse unfollow(long followerId, long followeeId) {
        followRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
        return new FollowResponse(false, followRepository.countByFolloweeId(followeeId));
    }

    /** 내 팔로잉 목록("나만" 봄). */
    public List<FollowingResponse> myFollowing(long followerId) {
        return followRepository.findMyFollowing(followerId).stream().map(FollowingResponse::of).toList();
    }
}
