package com.geuneul.domain.place;

/**
 * 생존 인프라 카테고리 (docs/SPEC.md §3 MVP 레이어).
 *
 * CAFE·STUDY_CAFE는 ADR-0006(공부 가능 공간 커버리지 확장)로 신설 — "무슨 장소인가(kind)"만 category로
 * 최소 응집한다. "공부 가능한가"는 별도 축(place_features.study_ok/quiet)이라 category를 늘리지 않는다.
 */
public enum PlaceCategory {
    COOLING_SHELTER("무더위쉼터"),
    TOILET("공중화장실"),
    WATER("음수대"),
    PARK("공원"),
    LIBRARY("도서관"),
    CIVIC("공공기관"),
    UNDERGROUND("지하상가"),
    CAFE("카페"),
    STUDY_CAFE("스터디카페"),
    ETC("기타");

    private final String label;

    PlaceCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * 상업 POI 여부 (places.is_commercial, ADR-0006) — "공개 커먼스" 정체성 방어를 위해
     * 커먼스(도서관·공공시설·쉼터 등)와 상업(카페류)을 분리 노출·필터할 수 있게 한다.
     */
    public boolean commercial() {
        return this == CAFE || this == STUDY_CAFE;
    }
}
