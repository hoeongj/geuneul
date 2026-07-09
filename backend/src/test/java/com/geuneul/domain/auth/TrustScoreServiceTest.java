package com.geuneul.domain.auth;

import com.geuneul.domain.report.ReportRepository;
import com.geuneul.domain.review.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DB 없는 순수 오케스트레이션 단위테스트 — Docker 없이 로컬에서 항상 돈다(colima 이슈 무관, TS-009).
 * 실 카운트 쿼리(idx_reports_user·idx_reviews_user, V6)는 IT/CI가 커버; 여기는 조립 로직만.
 */
class TrustScoreServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-09T12:00:00Z"), ZoneOffset.UTC);

    private UserRepository userRepository;
    private ReportRepository reportRepository;
    private ReviewRepository reviewRepository;
    private TrustScoreService trustScoreService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        reportRepository = mock(ReportRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        trustScoreService = new TrustScoreService(userRepository, reportRepository, reviewRepository, CLOCK);
    }

    private static User userCreatedDaysAgo(long id, long daysAgo) {
        User u = User.create(AuthProvider.KAKAO, "kid-" + id, null, "닉네임", null);
        setField(u, "id", id);
        setField(u, "createdAt", OffsetDateTime.now(CLOCK).minusDays(daysAgo));
        return u;
    }

    private static void setField(User user, String field, Object value) {
        try {
            Field f = User.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(user, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("활동·연령을 카운트해 TrustScore 공식대로 users.trust_score를 갱신·저장한다")
    void recalculatesAndSaves() {
        User user = userCreatedDaysAgo(10L, 90);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(reportRepository.countByUserId(10L)).thenReturn(20L);
        when(reviewRepository.countByUserId(10L)).thenReturn(5L);

        trustScoreService.recalculate(10L);

        double expected = TrustScore.calculate(20, 5, 90);
        assertThat(user.getTrustScore()).isCloseTo(expected, offset(0.001));
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("존재하지 않는 유저면 아무 것도 하지 않는다 (방어적 — 삭제된 유저의 뒤늦은 콜백 등)")
    void missingUserIsNoop() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        trustScoreService.recalculate(999L);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("무활동 신규 유저는 trust_score 0으로 갱신된다")
    void freshUserGetsZero() {
        User user = userCreatedDaysAgo(11L, 0);
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));
        when(reportRepository.countByUserId(11L)).thenReturn(0L);
        when(reviewRepository.countByUserId(11L)).thenReturn(0L);

        trustScoreService.recalculate(11L);

        assertThat(user.getTrustScore()).isEqualTo(0.0);
    }
}
