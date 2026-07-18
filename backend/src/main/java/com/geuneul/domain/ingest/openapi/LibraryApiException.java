package com.geuneul.domain.ingest.openapi;

/**
 * 도서관 오픈API 응답을 전량 스냅샷으로 신뢰할 수 없을 때 사용하는 값 비노출 예외.
 * 서비스키가 들어간 요청 URI나 외부 응답 본문을 메시지·원장에 복제하지 않는다.
 */
public class LibraryApiException extends RuntimeException {

    public LibraryApiException(int pageNo, String reason) {
        super("도서관 오픈API page=" + pageNo + " 수집 실패: " + reason);
    }
}
