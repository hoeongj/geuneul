package com.geuneul.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    long countByTargetTypeAndTargetIdAndType(ReactionTarget targetType, long targetId, ReactionType type);

    boolean existsByTargetTypeAndTargetIdAndUserIdAndType(
            ReactionTarget targetType, long targetId, long userId, ReactionType type);

    long deleteByTargetTypeAndTargetIdAndUserIdAndType(
            ReactionTarget targetType, long targetId, long userId, ReactionType type);
}
