package com.geuneul.domain.ingest.storeapi;

import java.util.List;

/**
 * data.go.kr 표준 오픈API 응답 포맷 가정(response.header/body.items) — 도서관 API(실측 확인)와
 * 같은 포털의 표준 스캐폴드를 따른다는 전제. ⚠️ 계약 미검증(StoreRecord 주석 참고) — 실제 필드명이
 * 다르면(body 바로 아래 items인 변형 등) {@link SmallBusinessStoreApiClient}만 고치면 된다
 * (호출부인 StoreIngestionService는 StorePage 추상화만 본다).
 */
public record StoreApiResponse(Response response) {

    public record Response(Header header, Body body) {
    }

    public record Header(String resultCode, String resultMsg) {
    }

    public record Body(List<StoreRecord> items, String totalCount, String numOfRows, String pageNo) {
    }
}
