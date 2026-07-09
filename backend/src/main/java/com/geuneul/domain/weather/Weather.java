package com.geuneul.domain.weather;

/**
 * 한 격자(nx,ny)의 "지금 날씨" — 기상청 초단기실황(getUltraSrtNcst)에서 뽑은 관측값.
 *
 * survival_score의 기온(comfort) 성분과 추천 rain 시나리오를 보강하는 데 쓴다. Redis에 TTL 캐시되므로
 * (같은 격자·같은 발표시각은 재조회하지 않음) 순수 값 객체로 둔다. 결측 성분은 null — 관측이 일부 카테고리를
 * 빼먹어도 나머지로 점수를 굴릴 수 있게 한다(방어적 재정규화는 조립 단계에서).
 *
 * @param temperatureC  기온(°C, T1H). 결측 시 null.
 * @param humidityPct   습도(%, REH). 결측 시 null.
 * @param rain1hMm      1시간 강수량(mm, RN1). "강수없음"은 0.0.
 * @param precipitation 강수 형태(PTY 정규화).
 * @param observedAt    발표(관측) 시각 "yyyyMMddHHmm" — freshness 표시·캐시 식별용.
 */
public record Weather(
        Double temperatureC,
        Integer humidityPct,
        Double rain1hMm,
        PrecipitationType precipitation,
        String observedAt
) {
}
