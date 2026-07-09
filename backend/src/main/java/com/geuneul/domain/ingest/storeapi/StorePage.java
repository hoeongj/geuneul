package com.geuneul.domain.ingest.storeapi;

import java.util.List;

/** 상가업소정보 API 1페이지 결과 — items가 비면 호출부가 페이지네이션을 멈춘다. */
public record StorePage(List<StoreRecord> items, int totalCount) {

    public static StorePage empty() {
        return new StorePage(List.of(), 0);
    }
}
