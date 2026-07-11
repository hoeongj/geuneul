package com.geuneul.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {

    List<NotificationRule> findTop100ByUserIdOrderByCreatedAtDesc(long userId);

    Optional<NotificationRule> findByIdAndUserId(long id, long userId);

    /** HEAT_ESCAPE 온디맨드 평가용 — 유저의 활성 규칙만(ADR-0020). 보통 0~1건. */
    List<NotificationRule> findByUserIdAndTypeAndActiveTrue(long userId, NotificationRuleType type);
}
