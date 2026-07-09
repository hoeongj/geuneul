package com.geuneul.domain.flag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 허위 제보/후기 신고 (CLAUDE.md §0-7 모더레이션, §8 flags — reports_flags/reviews_flags를
 * 통합한 단일 테이블, 근거는 V7 마이그레이션 주석·WORKLOG 참고). target은 REPORT|REVIEW를
 * 가리키는 다형 참조라 FK 대신 (targetType, targetId)만 갖는다 — 대상 존재 검증은
 * FlagService가 각 리포지토리로 확인한다.
 *
 * report/review와 동일한 경량 FK-id 패턴(연관관계 매핑 없이 id만 보관, 검증은 서비스에서).
 */
@Entity
@Table(name = "flags")
public class Flag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private FlagTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FlagReason reason;

    @Column(length = 500)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FlagStatus status = FlagStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected Flag() {
    }

    /** 신규 신고 접수 — 항상 PENDING에서 시작. reporterId는 컨트롤러가 JWT에서 취해 전달한다. */
    public static Flag create(FlagTargetType targetType, long targetId, long reporterId,
                              FlagReason reason, String detail) {
        Flag f = new Flag();
        f.targetType = targetType;
        f.targetId = targetId;
        f.reporterId = reporterId;
        f.reason = reason;
        f.detail = detail;
        f.status = FlagStatus.PENDING;
        return f;
    }

    /** 관리자 처리 — PENDING에서만 전이 가능(FlagService가 사전 검증), resolvedAt은 처리 시각. */
    public void resolve(FlagStatus newStatus, OffsetDateTime resolvedAt) {
        this.status = newStatus;
        this.resolvedAt = resolvedAt;
    }

    public Long getId() {
        return id;
    }

    public FlagTargetType getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public FlagReason getReason() {
        return reason;
    }

    public String getDetail() {
        return detail;
    }

    public FlagStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }
}
