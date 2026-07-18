package com.geuneul.domain.ingest.safetydata;

import java.util.List;

/** 무더위쉼터 오픈API 1페이지 결과. transport·계약 오류는 클라이언트가 예외로 분리한다. */
public record ShelterPage(List<ShelterRecord> items, int totalCount) {

    public static ShelterPage empty() {
        return new ShelterPage(List.of(), 0);
    }
}
