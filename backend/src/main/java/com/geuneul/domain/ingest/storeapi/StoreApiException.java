package com.geuneul.domain.ingest.storeapi;

/** 상권정보 API 실패를 serviceKey·요청 URI 없이 상위 인제스천 원장으로 전달한다. */
public class StoreApiException extends RuntimeException {

    public StoreApiException(int pageNo, String reason) {
        super("상권정보 API page=" + pageNo + " 수집 실패: " + reason);
    }
}
