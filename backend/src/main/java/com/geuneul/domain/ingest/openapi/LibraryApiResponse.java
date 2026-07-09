package com.geuneul.domain.ingest.openapi;

import java.util.List;

/**
 * data.go.kr 표준 오픈API 응답 포맷(response.header/body). 실측: 정상은 resultCode="00",
 * 요청 페이지에 데이터가 없으면(마지막 페이지 다음) resultCode="03"(NODATA_ERROR)이고 body 자체가
 * 아예 없다(빈 배열이 아니라 필드 부재) — body를 nullable로 두고 호출부가 null 체크한다.
 */
public record LibraryApiResponse(Response response) {

    public record Response(Header header, Body body) {
    }

    public record Header(String resultCode, String resultMsg) {
    }

    public record Body(List<PublicLibraryRecord> items, String totalCount, String numOfRows, String pageNo) {
    }
}
