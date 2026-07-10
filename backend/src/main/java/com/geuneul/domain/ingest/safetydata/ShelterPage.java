package com.geuneul.domain.ingest.safetydata;

import java.util.List;

/** 무더위쉼터 오픈API 1페이지 결과 — items가 비면 호출부가 페이지네이션을 멈춘다(정상 종료든 오류든). */
public record ShelterPage(List<ShelterRecord> items, int totalCount) {

    public static ShelterPage empty() {
        return new ShelterPage(List.of(), 0);
    }
}
