package com.geuneul.domain.community;

import com.geuneul.domain.auth.User;
import com.geuneul.domain.auth.UserRepository;
import com.geuneul.domain.community.dto.ReviewCommentResponse;
import com.geuneul.domain.review.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 후기 댓글(2차·살, CLAUDE.md §8) 오케스트레이션. survival_score(간판)와 무관한 커뮤니티 콘텐츠(§0-9).
 * 작성은 로그인 필요(userId는 컨트롤러가 JWT에서 뽑아 전달 — 요청 바디로 안 받는다, 신원 위조 방지).
 */
@Service
@Transactional(readOnly = true)
public class ReviewCommentService {

    private final ReviewCommentRepository commentRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public ReviewCommentService(ReviewCommentRepository commentRepository, ReviewRepository reviewRepository,
                                UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ReviewCommentResponse create(long reviewId, long userId, String comment) {
        if (!reviewRepository.existsById(reviewId)) {
            throw new ResponseStatusException(NOT_FOUND, "review not found: " + reviewId);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "user not found: " + userId));
        ReviewComment saved = commentRepository.save(ReviewComment.of(reviewId, userId, comment.strip()));
        return ReviewCommentResponse.of(saved, user.getNickname(), user.getProfileImage());
    }

    public List<ReviewCommentResponse> listByReview(long reviewId) {
        if (!reviewRepository.existsById(reviewId)) {
            throw new ResponseStatusException(NOT_FOUND, "review not found: " + reviewId);
        }
        return commentRepository.findByReviewIdWithAuthor(reviewId).stream()
                .map(ReviewCommentResponse::of)
                .toList();
    }
}
