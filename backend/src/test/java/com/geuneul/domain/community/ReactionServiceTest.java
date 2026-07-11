package com.geuneul.domain.community;

import com.geuneul.domain.report.ReportRepository;
import com.geuneul.domain.review.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReactionServiceTest {

    private ReactionRepository reactionRepository;
    private ReviewRepository reviewRepository;
    private ReportRepository reportRepository;
    private ReviewCommentRepository commentRepository;
    private ReactionService service;

    @BeforeEach
    void setUp() {
        reactionRepository = mock(ReactionRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        reportRepository = mock(ReportRepository.class);
        commentRepository = mock(ReviewCommentRepository.class);
        service = new ReactionService(reactionRepository, reviewRepository, reportRepository, commentRepository);
    }

    @Test
    @DisplayName("hidden 리뷰에는 리액션을 만들 수 없다")
    void hiddenReviewRejected() {
        when(reviewRepository.existsByIdAndHiddenFalse(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.add(ReactionTarget.REVIEW, 1L, 10L, ReactionType.HELPFUL))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(reactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("hidden 제보에는 리액션을 만들 수 없다")
    void hiddenReportRejected() {
        when(reportRepository.existsByIdAndHiddenFalse(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.add(ReactionTarget.REPORT, 1L, 10L, ReactionType.HELPFUL))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(reactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("부모 리뷰가 hidden인 댓글에는 리액션을 만들 수 없다")
    void commentUnderHiddenReviewRejected() {
        when(commentRepository.existsVisibleById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.add(ReactionTarget.COMMENT, 1L, 10L, ReactionType.HELPFUL))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(reactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("공개 리뷰 리액션은 저장하고 count를 반환한다")
    void visibleReviewReactionSaves() {
        when(reviewRepository.existsByIdAndHiddenFalse(1L)).thenReturn(true);
        when(reactionRepository.existsByTargetTypeAndTargetIdAndUserIdAndType(
                ReactionTarget.REVIEW, 1L, 10L, ReactionType.HELPFUL)).thenReturn(false);
        when(reactionRepository.countByTargetTypeAndTargetIdAndType(
                ReactionTarget.REVIEW, 1L, ReactionType.HELPFUL)).thenReturn(1L);

        service.add(ReactionTarget.REVIEW, 1L, 10L, ReactionType.HELPFUL);

        verify(reactionRepository).save(any(Reaction.class));
    }
}
