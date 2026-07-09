package com.geuneul.domain.ingest.openapi;

import java.util.List;

/** 도서관 오픈API 1페이지 결과 — items가 비면 호출부가 페이지네이션을 멈춘다(정상 종료든 오류든). */
public record LibraryPage(List<PublicLibraryRecord> items, int totalCount) {

    public static LibraryPage empty() {
        return new LibraryPage(List.of(), 0);
    }
}
