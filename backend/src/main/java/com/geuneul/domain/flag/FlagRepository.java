package com.geuneul.domain.flag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlagRepository extends JpaRepository<Flag, Long> {

    /** 중복 신고 판정(같은 유저·같은 대상) — DB 유니크 제약(V7)의 애플리케이션 레벨 사전 체크. */
    boolean existsByTargetTypeAndTargetIdAndReporterId(FlagTargetType targetType, long targetId, long reporterId);

    /** 관리자 검수 큐 — 오래된 신고부터(FIFO) 처리하도록 오름차순. */
    Page<Flag> findByStatusOrderByCreatedAtAsc(FlagStatus status, Pageable pageable);
}
