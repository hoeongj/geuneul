package com.geuneul.domain.report;

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
 * 휘발성 제보 — "지금 상태"의 원천 데이터 (스키마 소유권은 Flyway V2, JPA는 검증만).
 * expires_at이 지나면 조회·freshness 스코어(P3)에서 제외된다. 후기(review, 영구 평판)와 구분.
 * place/user는 연관 로딩이 필요 없어 FK id 컬럼만 매핑한다(경량 — 검증은 서비스에서).
 */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 익명(비로그인) 제보는 null. P2 인증 붙으면 채워진다. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 24)
    private ReportType reportType;

    @Column(length = 500)
    private String comment;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    protected Report() {
    }

    public static Report anonymous(long placeId, ReportType type, String comment,
                                    boolean anonymousFlag, OffsetDateTime expiresAt) {
        Report r = new Report();
        r.placeId = placeId;
        r.reportType = type;
        r.comment = comment;
        r.anonymous = anonymousFlag;
        r.expiresAt = expiresAt;
        return r;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public String getComment() {
        return comment;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
