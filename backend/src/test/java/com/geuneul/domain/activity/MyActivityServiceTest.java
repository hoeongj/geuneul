package com.geuneul.domain.activity;

import com.geuneul.domain.activity.dto.MyCommentResponse;
import com.geuneul.domain.activity.dto.MyReactionResponse;
import com.geuneul.domain.activity.dto.MyReviewResponse;
import com.geuneul.domain.community.ReactionRepository;
import com.geuneul.domain.community.ReviewCommentRepository;
import com.geuneul.domain.review.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "내 글 관리"(N6) 서비스 매핑 단위테스트 — view→DTO 변환과 Instant→UTC 부착(TS-016)을 Docker 없이 검증.
 * 실 조인 SQL(장소명·후기·리액션 조인)은 MyActivityFlowIT(실 Postgres, CI)가 검증한다.
 */
class MyActivityServiceTest {

    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final ReviewCommentRepository reviewCommentRepository = mock(ReviewCommentRepository.class);
    private final ReactionRepository reactionRepository = mock(ReactionRepository.class);
    private final MyActivityService service =
            new MyActivityService(reviewRepository, reviewCommentRepository, reactionRepository);

    private static final Instant T = Instant.parse("2026-07-11T03:00:00Z");

    @Test
    @DisplayName("내 후기: view→DTO 매핑 + createdAt UTC 부착")
    void myReviews() {
        MyReviewView v = mock(MyReviewView.class);
        when(v.getId()).thenReturn(7L);
        when(v.getPlaceId()).thenReturn(3L);
        when(v.getPlaceName()).thenReturn("상도1동 무더위쉼터");
        when(v.getRating()).thenReturn(5);
        when(v.getComment()).thenReturn("시원해요");
        when(v.getCreatedAt()).thenReturn(T);
        when(reviewRepository.findByUserIdWithPlace(10L)).thenReturn(List.of(v));

        List<MyReviewResponse> result = service.myReviews(10L);

        assertThat(result).hasSize(1);
        MyReviewResponse r = result.get(0);
        assertThat(r.reviewId()).isEqualTo(7L);
        assertThat(r.placeId()).isEqualTo(3L);
        assertThat(r.placeName()).isEqualTo("상도1동 무더위쉼터");
        assertThat(r.rating()).isEqualTo(5);
        assertThat(r.createdAt()).isEqualTo(T.atOffset(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("내 댓글: reviewId/placeId/placeName 매핑")
    void myComments() {
        MyCommentView v = mock(MyCommentView.class);
        when(v.getId()).thenReturn(2L);
        when(v.getReviewId()).thenReturn(7L);
        when(v.getPlaceId()).thenReturn(3L);
        when(v.getPlaceName()).thenReturn("노들서가");
        when(v.getComment()).thenReturn("동의해요");
        when(v.getCreatedAt()).thenReturn(T);
        when(reviewCommentRepository.findByUserIdWithPlace(10L)).thenReturn(List.of(v));

        List<MyCommentResponse> result = service.myComments(10L);

        assertThat(result).hasSize(1);
        MyCommentResponse c = result.get(0);
        assertThat(c.commentId()).isEqualTo(2L);
        assertThat(c.reviewId()).isEqualTo(7L);
        assertThat(c.placeName()).isEqualTo("노들서가");
        assertThat(c.createdAt()).isEqualTo(OffsetDateTime.parse("2026-07-11T03:00:00Z"));
    }

    @Test
    @DisplayName("내 유용해요: reviewComment 미리보기 포함 매핑")
    void myReactions() {
        MyReactionView v = mock(MyReactionView.class);
        when(v.getId()).thenReturn(9L);
        when(v.getReviewId()).thenReturn(7L);
        when(v.getPlaceId()).thenReturn(3L);
        when(v.getPlaceName()).thenReturn("종로도서관");
        when(v.getReviewComment()).thenReturn("콘센트 많아요");
        when(v.getCreatedAt()).thenReturn(T);
        when(reactionRepository.findMyReviewReactions(10L)).thenReturn(List.of(v));

        List<MyReactionResponse> result = service.myReactions(10L);

        assertThat(result).hasSize(1);
        MyReactionResponse r = result.get(0);
        assertThat(r.reactionId()).isEqualTo(9L);
        assertThat(r.reviewId()).isEqualTo(7L);
        assertThat(r.reviewComment()).isEqualTo("콘센트 많아요");
        assertThat(r.placeName()).isEqualTo("종로도서관");
    }

    @Test
    @DisplayName("활동이 없으면 빈 리스트")
    void emptyWhenNoActivity() {
        when(reviewRepository.findByUserIdWithPlace(10L)).thenReturn(List.of());
        assertThat(service.myReviews(10L)).isEmpty();
    }
}
