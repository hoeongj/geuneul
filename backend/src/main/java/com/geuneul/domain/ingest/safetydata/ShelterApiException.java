package com.geuneul.domain.ingest.safetydata;

/** 무더위쉼터 API 실패를 serviceKey·요청 URI·응답 본문 없이 상위 원장으로 전달한다. */
public class ShelterApiException extends RuntimeException {

    public ShelterApiException(int pageNo, String reason) {
        super("무더위쉼터 API page=" + pageNo + " 수집 실패: " + reason);
    }
}
