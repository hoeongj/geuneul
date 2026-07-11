package com.geuneul.domain.community;

import com.geuneul.domain.auth.User;
import com.geuneul.domain.auth.UserRepository;
import com.geuneul.domain.review.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewCommentServiceTest {

    private ReviewCommentRepository commentRepository;
    private ReviewRepository reviewRepository;
    private UserRepository userRepository;
    private ReviewCommentService service;

    @BeforeEach
    void setUp() {
        commentRepository = mock(ReviewCommentRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        userRepository = mock(UserRepository.class);
        service = new ReviewCommentService(commentRepository, reviewRepository, userRepository);
    }

    @Test
    @DisplayName("hidden 리뷰에는 댓글을 달 수 없고 404로 응답한다")
    void createRejectsHiddenReview() {
        when(reviewRepository.existsByIdAndHiddenFalse(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(1L, 10L, "댓글"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("hidden 리뷰의 댓글 목록도 404로 노출하지 않는다")
    void listRejectsHiddenReview() {
        when(reviewRepository.existsByIdAndHiddenFalse(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.listByReview(1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(commentRepository, never()).findByReviewIdWithAuthor(1L);
    }

    @Test
    @DisplayName("공개 리뷰 댓글 작성은 저장한다")
    void createVisibleReview() {
        User user = mock(User.class);
        when(user.getNickname()).thenReturn("작성자");
        when(reviewRepository.existsByIdAndHiddenFalse(1L)).thenReturn(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(commentRepository.save(any(ReviewComment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(1L, 10L, "댓글");

        verify(commentRepository).save(any(ReviewComment.class));
    }

    @Test
    @DisplayName("공개 리뷰 댓글 목록은 레포지토리 결과를 매핑한다")
    void listVisibleReview() {
        when(reviewRepository.existsByIdAndHiddenFalse(1L)).thenReturn(true);
        when(commentRepository.findByReviewIdWithAuthor(1L)).thenReturn(List.of());

        service.listByReview(1L);

        verify(commentRepository).findByReviewIdWithAuthor(1L);
    }
}
