package com.geuneul.domain.community;

import com.geuneul.domain.activity.MyReactionView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    long countByTargetTypeAndTargetIdAndType(ReactionTarget targetType, long targetId, ReactionType type);

    boolean existsByTargetTypeAndTargetIdAndUserIdAndType(
            ReactionTarget targetType, long targetId, long userId, ReactionType type);

    long deleteByTargetTypeAndTargetIdAndUserIdAndType(
            ReactionTarget targetType, long targetId, long userId, ReactionType type);

    /**
     * 내가 "유용해요" 누른 후기 목록(N6) — reactions × reviews × places 조인, 최신순.
     * 후기 대상 리액션만(target_type='REVIEW'). 리액션이 가리키는 후기의 장소로 이동한다.
     */
    @Query(value = """
            SELECT rx.id AS id, rx.target_id AS reviewId, rv.place_id AS placeId, p.name AS placeName,
                   rv.comment AS reviewComment, rx.created_at AS createdAt
            FROM reactions rx
            JOIN reviews rv ON rv.id = rx.target_id
            JOIN places p ON p.id = rv.place_id
            WHERE rx.user_id = :userId AND rx.target_type = 'REVIEW'
            ORDER BY rx.created_at DESC
            """, nativeQuery = true)
    List<MyReactionView> findMyReviewReactions(@Param("userId") long userId);
}
