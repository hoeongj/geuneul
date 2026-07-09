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

    /** 비로그인 제보는 null. 로그인 제보는 is_anonymous 표시 여부와 무관하게 채워진다(TrustScoreService 근거). */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 24)
    private ReportType reportType;

    @Column(length = 500)
    private String comment;

    /** P2 사진 presign(PhotoController) 슬롯 — S3 오브젝트 URL. 없으면 null(사진 없는 제보가 기본). */
    @Column(name = "photo_url", length = 512)
    private String photoUrl;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    /** GPS 방문 인증(ADR-0005 §④): 제보 시 제보자 좌표가 장소 100m 이내면 true. survival_score에서 가중(V10). */
    @Column(name = "verified", nullable = false)
    private boolean verified;

    /** 모더레이션 숨김(V12): 신고 RESOLVED 시 true. 공개 조회·스코어·급증·혼잡에서 제외. */
    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    // created_at은 Hibernate @CreationTimestamp(JVM 클록), expires_at은 서비스가 주입 Clock으로 산정,
    // freshness/만료 판정은 place_report_signals 뷰가 DB now()로 한다. 세 클록이 프로덕션에선 모두 UTC라
    // 실질 오차는 sub-second. (테스트의 fake Clock은 @CreationTimestamp까진 못 바꾸므로 IT는 "방금 생성" 기준으로 검증.)
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    protected Report() {
    }

    /** 비로그인(익명) 제보 — userId 없이 생성(비검증). 기존 호출부·테스트 호환을 위해 유지({@link #of} 위임). */
    public static Report anonymous(long placeId, ReportType type, String comment,
                                    boolean anonymousFlag, OffsetDateTime expiresAt) {
        return of(null, placeId, type, comment, null, anonymousFlag, false, expiresAt);
    }

    /**
     * 로그인 여부와 무관한 공통 생성 진입점. userId가 있으면 trust_score 가중 대상이 된다
     * (V4 place_report_signals 뷰가 user_id로 users.trust_score를 조인) — is_anonymous(표시 여부)와는
     * 별개다: 로그인 유저가 "익명으로 제보"를 선택해도(CLAUDE.md §6 "익명 여부") userId는 그대로 기록해
     * 신뢰도 가중은 유지하고, 화면 표시만 감춘다. photoUrl은 P2 사진 presign(PhotoController) 슬롯(없으면 null).
     * verified는 GPS 방문 인증(ADR-0005 §④): 제보자 좌표가 장소 100m 이내면 true → V10 뷰에서 가중.
     */
    public static Report of(Long userId, long placeId, ReportType type, String comment, String photoUrl,
                            boolean anonymousFlag, boolean verified, OffsetDateTime expiresAt) {
        Report r = new Report();
        r.userId = userId;
        r.placeId = placeId;
        r.reportType = type;
        r.comment = comment;
        r.photoUrl = photoUrl;
        r.anonymous = anonymousFlag;
        r.verified = verified;
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

    public String getPhotoUrl() {
        return photoUrl;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public boolean isVerified() {
        return verified;
    }

    public boolean isHidden() {
        return hidden;
    }

    /** 모더레이션 숨김 처리(신고 RESOLVED) — 되돌리기 경로는 이번 스코프 밖(관리자 수동). */
    public void hide() {
        this.hidden = true;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
