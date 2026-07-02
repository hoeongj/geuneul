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

    @Column(name = "external_map_url", length = 512)
    private String externalMapUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "open_hours_json")
    private String openHoursJson;

    @Column(nullable = false)
    private boolean geocoded;

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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
