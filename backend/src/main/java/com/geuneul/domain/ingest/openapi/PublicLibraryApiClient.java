package com.geuneul.domain.ingest.openapi;

/** 전국도서관표준데이터 오픈API 접근 — 구현체: {@link DataGoKrPublicLibraryClient}. 테스트는 페이크로 대체. */
public interface PublicLibraryApiClient {

    LibraryPage fetchPage(int pageNo, int numOfRows);
}
