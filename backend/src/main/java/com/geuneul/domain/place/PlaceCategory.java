package com.geuneul.domain.place;

/**
 * 생존 인프라 카테고리 (CLAUDE.md §3 MVP 레이어).
 */
public enum PlaceCategory {
    COOLING_SHELTER("무더위쉼터"),
    TOILET("공중화장실"),
    WATER("음수대"),
    PARK("공원"),
    LIBRARY("도서관"),
    CIVIC("공공기관"),
    UNDERGROUND("지하상가"),
    ETC("기타");

    private final String label;

    PlaceCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
