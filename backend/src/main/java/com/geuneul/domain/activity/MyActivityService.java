package com.geuneul.domain.activity;

import com.geuneul.domain.activity.dto.MyCommentResponse;
import com.geuneul.domain.activity.dto.MyReactionResponse;
import com.geuneul.domain.activity.dto.MyReviewResponse;
import com.geuneul.domain.community.ReactionRepository;
import com.geuneul.domain.community.ReviewCommentRepository;
import com.geuneul.domain.review.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * "내 글 관리"(N6) — 로그인 유저가 자신이 쓴 후기/댓글/유용해요를 모아본다(각 항목에서 원문 장소로 이동).
 * 커뮤니티는 "살"이라 survival_score(간판)와 무관하다(§0-9). 조회 전용, 각 리포지토리의 유저 필터 쿼리를 조립만 한다.
 */
@Service
@Transactional(readOnly = true)
public class MyActivityService {

    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final ReactionRepository reactionRepository;

    public MyActivityService(ReviewRepository reviewRepository,
                             ReviewCommentRepository reviewCommentRepository,
                             ReactionRepository reactionRepository) {
        this.reviewRepository = reviewRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.reactionRepository = reactionRepository;
    }

    public List<MyReviewResponse> myReviews(long userId) {
        return reviewRepository.findByUserIdWithPlace(userId).stream().map(MyReviewResponse::of).toList();
    }

    public List<MyCommentResponse> myComments(long userId) {
        return reviewCommentRepository.findByUserIdWithPlace(userId).stream().map(MyCommentResponse::of).toList();
    }

    public List<MyReactionResponse> myReactions(long userId) {
        return reactionRepository.findMyReviewReactions(userId).stream().map(MyReactionResponse::of).toList();
    }
}
