package com.geuneul.domain.ingest.storeapi;

/**
 * 상가(상권)정보 오픈API 접근 — 반경 검색(storeListInRadius류)을 쓴다. 행정동코드 기반
 * storeListInDong 대신 반경을 쓴 이유(ADR-0006): 전국 행정동코드 목록(별도 데이터셋)을 몰라도
 * 이미 그늘이 쓰는 lat/lng/radius 개념 그대로 격자 순회로 전국을 커버할 수 있다(우리 PostGIS
 * 반경검색과 동일한 정신모델 — /places?lat=&lng=&radius= 와 대칭).
 * 구현체: {@link SmallBusinessStoreApiClient}. 계약 미검증(StoreRecord 주석 참고).
 */
public interface StoreApiClient {

    StorePage searchByRadius(double lat, double lng, int radiusMeters, String indsLclsCd,
                             int pageNo, int numOfRows);
}
