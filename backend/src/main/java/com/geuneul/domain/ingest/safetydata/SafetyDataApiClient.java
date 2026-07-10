package com.geuneul.domain.ingest.safetydata;

/** 무더위쉼터 오픈API(safetydata.go.kr) 접근 — 구현체: {@link SafetyDataShelterClient}. 테스트는 페이크/Mock으로 대체. */
public interface SafetyDataApiClient {

    ShelterPage fetchPage(int pageNo, int numOfRows);
}
