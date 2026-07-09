package com.geuneul.domain.auth;

import com.geuneul.domain.report.ReportRepository;
import com.geuneul.domain.review.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * trust_score 재계산 오케스트레이션 (CLAUDE.md §5 "제보는 trust_score로 가중"의 실제 산출, {@link TrustScore}
 * 참고). ReportService·ReviewService가 로그인 유저의 제보/후기 저장 직후 호출한다.
 *
 * <p><b>재계산 시점 = 온디맨드(배치 아님) — WORKLOG 2026-07-09 근거</b>:
 * <ul>
 *   <li>UGC는 저빈도(레이트리밋 분당 3·시간당 10)라 유저당 재계산 비용(카운트 쿼리 2회)이 미미하다 —
 *       전체 유저를 도는 배치 잡이 얻을 성능 이득이 없다.</li>
 *   <li>이 프로젝트에 스케줄러 인프라(EventBridge)는 P3 공공데이터 동기화용으로만 계획돼 있다.
 *       trust_score 하나 때문에 새 스케줄 인프라를 얹는 건 과설계(CLAUDE.md §0.2 "범위를 스스로 늘리지 않는다").</li>
 *   <li>{@code place_report_signals} 뷰(V4)가 조회 시점에 users.trust_score를 그대로 읽으므로, 배치 지연 없이
 *       "방금 로그인해 첫 제보를 남긴 유저"도 다음 제보부터 즉시 신뢰도가 반영되는 게 제품 경험상 자연스럽다.</li>
 * </ul>
 */
@Service
public class TrustScoreService {

    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final ReviewRepository reviewRepository;
    private final Clock clock;

    public TrustScoreService(UserRepository userRepository, ReportRepository reportRepository,
                             ReviewRepository reviewRepository, Clock clock) {
        this.userRepository = userRepository;
        this.reportRepository = reportRepository;
        this.reviewRepository = reviewRepository;
        this.clock = clock;
    }

    /**
     * 유저의 제보/후기 수·계정 연령으로 trust_score를 재계산해 저장한다.
     * 유저가 없으면(이론상 불가하지만 방어적으로) 아무 일도 하지 않는다.
     */
    @Transactional
    public void recalculate(long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            long reportCount = reportRepository.countByUserId(userId);
            long reviewCount = reviewRepository.countByUserId(userId);
            long accountAgeDays = Duration.between(user.getCreatedAt(), OffsetDateTime.now(clock)).toDays();
            double score = TrustScore.calculate(reportCount, reviewCount, accountAgeDays);
            user.updateTrustScore(score);
            userRepository.save(user);
        });
    }
}
