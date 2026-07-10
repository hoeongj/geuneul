package com.geuneul.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {

    List<NotificationRule> findByUserIdOrderByCreatedAtDesc(long userId);

    Optional<NotificationRule> findByIdAndUserId(long id, long userId);
}
