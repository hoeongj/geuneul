package com.geuneul.domain.place;

import com.geuneul.domain.place.FeatureGrade.Polarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시설 속성 등급화(순수 함수) 단위테스트 — 콘센트/wifi 3단계, noise_level(신설), 불리언 시설.
 */
class FeatureGradeTest {

    @Test
    @DisplayName("콘센트: many/some/few 3단계 + 라벨")
    void outletScale() {
        assertThat(FeatureGrade.of("outlet", "many").level()).isEqualTo("MANY");
        assertThat(FeatureGrade.of("outlet", "many").label()).isEqualTo("콘센트 많음");
        assertThat(FeatureGrade.of("outlet", "few").level()).isEqualTo("FEW");
        assertThat(FeatureGrade.of("outlet", "some").polarity()).isEqualTo(Polarity.POSITIVE);
    }

    @Test
    @DisplayName("콘센트 true는 '있음'(SOME), false/none은 미표시(present=false)")
    void outletBooleanFallback() {
        assertThat(FeatureGrade.of("outlet", "true").level()).isEqualTo("SOME");
        assertThat(FeatureGrade.of("outlet", "true").present()).isTrue();
        assertThat(FeatureGrade.of("outlet", "false").present()).isFalse();
        assertThat(FeatureGrade.of("outlet", "none").present()).isFalse();
    }

    @Test
    @DisplayName("wifi: fast/ok/slow 등급")
    void wifiScale() {
        assertThat(FeatureGrade.of("wifi", "fast").label()).isEqualTo("와이파이 빠름");
        assertThat(FeatureGrade.of("wifi", "slow").level()).isEqualTo("FEW");   // slow=low=FEW 슬롯
    }

    @Test
    @DisplayName("noise_level(신설): quiet=POSITIVE, loud=NEGATIVE, moderate=NEUTRAL")
    void noiseLevel() {
        assertThat(FeatureGrade.of("noise_level", "quiet").polarity()).isEqualTo(Polarity.POSITIVE);
        assertThat(FeatureGrade.of("noise_level", "quiet").label()).isEqualTo("조용함");
        assertThat(FeatureGrade.of("noise_level", "loud").polarity()).isEqualTo(Polarity.NEGATIVE);
        assertThat(FeatureGrade.of("noise_level", "moderate").polarity()).isEqualTo(Polarity.NEUTRAL);
    }

    @Test
    @DisplayName("불리언 시설: study_ok/quiet/air_conditioned true면 present + POSITIVE")
    void booleanFeatures() {
        assertThat(FeatureGrade.of("study_ok", "true").present()).isTrue();
        assertThat(FeatureGrade.of("study_ok", "true").label()).isEqualTo("공부 가능");
        assertThat(FeatureGrade.of("air_conditioned", "true").polarity()).isEqualTo(Polarity.POSITIVE);
        assertThat(FeatureGrade.of("quiet", "false").present()).isFalse();
    }

    @Test
    @DisplayName("알 수 없는 타입은 원문 value 라벨 + NEUTRAL 폴백")
    void unknownType() {
        FeatureGrade g = FeatureGrade.of("mystery", "somevalue");
        assertThat(g.polarity()).isEqualTo(Polarity.NEUTRAL);
        assertThat(g.label()).isEqualTo("somevalue");
    }

    @Test
    @DisplayName("value 대소문자·공백 무관")
    void caseInsensitive() {
        assertThat(FeatureGrade.of("outlet", "  MANY ").level()).isEqualTo("MANY");
    }
}
