package com.geuneul.domain.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.OffsetDateTime;

/**
 * 장소 — 공공데이터·시드·유저 제안이 모두 이 테이블로 수렴한다.
 * (source, source_external_id)가 재적재 멱등 upsert의 자연키 (ADR-0002).
 * 좌표는 geometry(Point,4326) 단일 컬럼 — 스키마 소유권은 Flyway(V2), JPA는 검증만.
 */
@Entity
@Table(name = "places")
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlaceCategory category;

    private String address;

    @Column(nullable = false, columnDefinition = "geometry(Point,4326)")
    private Point geom;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "source_external_id", length = 128)
    private String sourceExternalId;

    // 아래 두 컬럼은 ERD(V2)에 선반영된 확장 슬롯 — 상세의 외부 지도 링크·운영시간(P2/P3)용.
    // 현재 인제스천/응답 경로는 채우지 않는다(값이 붙으면 상세 API가 노출).
    @Column(name = "external_map_url", length = 512)
    private String externalMapUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "open_hours_json")
    private String openHoursJson;

    @Column(nullable = false)
    private boolean geocoded;

    // ADR-0006: 상업(카페) vs 커먼스(도서관/공공시설 등) 분리 — "공개 커먼스" 정체성을 지도 필터에서 방어.
    @Column(name = "is_commercial", nullable = false)
    private boolean commercial;

    // ADR-0006: 스냅샷에서 사라진 행 soft-delete(폐업 회전 대응). NULL=활성.
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Place() {
    }

    public static Place of(String name, PlaceCategory category, String address, Point geom,
                           String source, String sourceExternalId) {
        Place p = new Place();
        p.name = name;
        p.category = category;
        p.address = address;
        p.geom = geom;
        p.source = source;
        p.sourceExternalId = sourceExternalId;
        p.geocoded = false;
        p.commercial = category != null && category.commercial();
        return p;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public PlaceCategory getCategory() {
        return category;
    }

    public String getAddress() {
        return address;
    }

    public Point getGeom() {
        return geom;
    }

    public String getSource() {
        return source;
    }

    public String getSourceExternalId() {
        return sourceExternalId;
    }

    public String getExternalMapUrl() {
        return externalMapUrl;
    }

    public String getOpenHoursJson() {
        return openHoursJson;
    }

    public boolean isGeocoded() {
        return geocoded;
    }

    public boolean isCommercial() {
        return commercial;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
