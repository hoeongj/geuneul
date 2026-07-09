package com.geuneul.domain.alert;

/**
 * 급증 장소 투영(ADR-0016) — bounds 뷰포트 내 "최근 시간창에 유효 제보가 임계 이상 몰린 장소".
 * ReportSurgeRepository의 네이티브 쿼리 결과를 담는다(Spring Data 인터페이스 프로젝션).
 */
public interface SurgingPlaceView {
    long getPlaceId();

    String getName();

    double getLat();

    double getLng();

    long getReportCount();

    /** 시간창 내 최빈 제보 타입(mode). 안내 문구 생성에 쓴다. */
    String getTopType();
}
