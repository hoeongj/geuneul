package com.geuneul.domain.weather;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 기온(체감) → comfort 매핑 단위 테스트(ADR-0009). DB·네트워크 불필요 — 순수 함수만 검증한다.
 * 체감온도 공식은 기상청 2022-06-02 개정 여름철 공식(습구온도 Stull 근사), comfort 앵커는
 * 2026 기상청 폭염특보 체감온도 임계값(33·35·38도)에 그대로 맞췄다.
 */
class HeatComfortTest {

    @Test
    @DisplayName("날씨 자체가 없으면 comfort 신호 없음(null) — 호출부는 제보 comfort로 폴백")
    void nullWeatherIsNoSignal() {
        assertThat(HeatComfort.comfortScore(null)).isNull();
    }

    @Test
    @DisplayName("기온 결측(관측 실패)이면 comfort 신호 없음(null)")
    void missingTemperatureIsNoSignal() {
        Weather w = new Weather(null, 60, 0.0, PrecipitationType.NONE, "202607091300");
        assertThat(HeatComfort.comfortScore(w)).isNull();
    }

    @Test
    @DisplayName("쾌적 기온(23도, 여름 기준 25도 이하) → comfort 1.0(쾌적 상한)")
    void comfortableTemperatureIsCeiling() {
        Weather w = new Weather(23.0, 55, 0.0, PrecipitationType.NONE, "202607091300");
        assertThat(HeatComfort.comfortScore(w)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("폭염주의보 체감 33도 근방 → comfort가 쾌적(1.0)보다 뚜렷이 낮다")
    void heatWarningLowersComfort() {
        Weather cool = new Weather(23.0, 55, 0.0, PrecipitationType.NONE, "202607091300");
        Weather hot = new Weather(33.0, 70, 0.0, PrecipitationType.NONE, "202607091300");

        Double coolComfort = HeatComfort.comfortScore(cool);
        Double hotComfort = HeatComfort.comfortScore(hot);

        assertThat(hotComfort).isLessThan(coolComfort);
        assertThat(hotComfort).isLessThanOrEqualTo(0.5); // 33도(습도 70) 체감은 33도를 넘어 0.4 이하권
    }

    @Test
    @DisplayName("폭염중대경보 체감 38도 이상 → comfort 0.0(하한 고정)")
    void emergencyHeatIsZeroComfort() {
        // 습도 90%면 체감온도가 기온보다 높게 나와 38도 문턱을 넘는다(Stull 습구온도 공식).
        Weather w = new Weather(37.0, 90, 0.0, PrecipitationType.NONE, "202607091300");
        assertThat(HeatComfort.comfortScore(w)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("강수 중이면 같은 기온이라도 comfort가 소폭(-0.15) 낮다")
    void precipitationPenalizesComfort() {
        Weather dry = new Weather(28.0, 60, 0.0, PrecipitationType.NONE, "202607091300");
        Weather rainy = new Weather(28.0, 60, 3.0, PrecipitationType.RAIN, "202607091300");

        double dryComfort = HeatComfort.comfortScore(dry);
        double rainyComfort = HeatComfort.comfortScore(rainy);

        assertThat(dryComfort - rainyComfort).isCloseTo(0.15, within(1e-9));
    }

    @Test
    @DisplayName("강수 페널티도 [0,1] 밖으로 나가지 않게 clamp된다")
    void precipitationPenaltyClamped() {
        // 이미 comfort가 0에 가까운 폭염 상황에서 비까지 오면 0 밑으로 내려가지 않아야 한다.
        Weather w = new Weather(40.0, 90, 5.0, PrecipitationType.RAIN, "202607091300");
        Double comfort = HeatComfort.comfortScore(w);
        assertThat(comfort).isEqualTo(0.0);
    }

    @Test
    @DisplayName("체감온도 매핑: 25도 이하 평평하게 1.0, 그 위로는 단조 감소")
    void mapTemperatureIsMonotonicAboveCeiling() {
        assertThat(HeatComfort.mapTemperature(10)).isEqualTo(1.0);
        assertThat(HeatComfort.mapTemperature(25)).isEqualTo(1.0);
        assertThat(HeatComfort.mapTemperature(29)).isCloseTo(0.7, within(1e-9)); // 25~33 구간 중점
        assertThat(HeatComfort.mapTemperature(33)).isCloseTo(0.4, within(1e-9));
        assertThat(HeatComfort.mapTemperature(35)).isCloseTo(0.2, within(1e-9));
        assertThat(HeatComfort.mapTemperature(38)).isCloseTo(0.0, within(1e-9));
        assertThat(HeatComfort.mapTemperature(42)).isEqualTo(0.0); // 상한 초과는 0으로 고정(리터럴 반환)
    }

    @Test
    @DisplayName("습도 결측이면 체감온도 공식 대신 원 기온을 그대로 쓴다")
    void missingHumidityFallsBackToRawTemperature() {
        assertThat(HeatComfort.feelsLikeC(30.0, null)).isEqualTo(30.0);
    }

    @Test
    @DisplayName("공식 검증 구간(20도) 미만이면 원 기온을 그대로 쓴다")
    void belowFormulaDomainFallsBackToRawTemperature() {
        assertThat(HeatComfort.feelsLikeC(15.0, 80)).isEqualTo(15.0);
    }
}
