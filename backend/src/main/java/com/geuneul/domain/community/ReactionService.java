package com.geuneul.domain.community;

import com.geuneul.domain.community.dto.ReactionResponse;
import com.geuneul.domain.report.ReportRepository;
import com.geuneul.domain.review.ReviewRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 리액션("유용했어요" 등, 2차·살) 오케스트레이션. 다형 대상(REVIEW/REPORT/COMMENT)의 존재를 종류별
 * 리포지토리로 검증한 뒤 추가/삭제한다. uq_reaction 유니크로 중복 추가는 멱등(이미 있으면 no-op).
 * survival_score(간판)와 무관 — 리액션은 커뮤니티 신호일 뿐 스코어에 들어가지 않는다(§0-9).
 */
@Service
@Transactional(readOnly = true)
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final ReviewRepository reviewRepository;
    private final ReportRepository reportRepository;
    private final ReviewCommentRepository commentRepository;

    public ReactionService(ReactionRepository reactionRepository, ReviewRepository reviewRepository,
                           ReportRepository reportRepository, ReviewCommentRepository commentRepository) {
        this.reactionRepository = reactionRepository;
        this.reviewRepository = reviewRepository;
        this.reportRepository = reportRepository;
        this.commentRepository = commentRepository;
    }

    /**
     * 리액션 추가(멱등) — 이미 있으면 그대로. reacted=true + 갱신된 count 반환.
     *
     * <p>NOT_SUPPORTED로 각 리포 호출을 자기 트랜잭션에 둔다(TS-031) — 동시 이중요청에서 뒤늦은 save가
     * uq_reaction 충돌로 예외를 던지면, 하나의 @Transactional이면 그 INSERT 실패가 트랜잭션을 오염시켜
     * (PG 25P02) 이어지는 count SELECT가 500이 된다. 멱등성은 uq_reaction이 보장하므로 트랜잭션을 분리한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReactionResponse add(ReactionTarget target, long targetId, long userId, ReactionType type) {
        requireTarget(target, targetId);
        if (!reactionRepository.existsByTargetTypeAndTargetIdAndUserIdAndType(target, targetId, userId, type)) {
            try {
                reactionRepository.save(Reaction.of(target, targetId, userId, type));
            } catch (DataIntegrityViolationException race) {
                // 동시 요청이 먼저 넣은 경우(uq_reaction 충돌) — 멱등이므로 무시하고 현재 상태를 반환.
            }
        }
        return ReactionResponse.of(true, count(target, targetId, type));
    }

    /** 리액션 삭제 — 없으면 no-op. reacted=false + 갱신된 count 반환. */
    @Transactional
    public ReactionResponse remove(ReactionTarget target, long targetId, long userId, ReactionType type) {
        requireTarget(target, targetId);
        reactionRepository.deleteByTargetTypeAndTargetIdAndUserIdAndType(target, targetId, userId, type);
        return ReactionResponse.of(false, count(target, targetId, type));
    }

    private long count(ReactionTarget target, long targetId, ReactionType type) {
        return reactionRepository.countByTargetTypeAndTargetIdAndType(target, targetId, type);
    }

    private void requireTarget(ReactionTarget target, long targetId) {
        boolean exists = switch (target) {
            case REVIEW -> reviewRepository.existsById(targetId);
            case REPORT -> reportRepository.existsById(targetId);
            case COMMENT -> commentRepository.existsById(targetId);
        };
        if (!exists) {
            throw new ResponseStatusException(NOT_FOUND, "reaction target not found: " + target + "#" + targetId);
        }
    }
}
