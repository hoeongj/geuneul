package com.geuneul.domain.weather.dto;

import com.geuneul.domain.weather.PrecipitationType;
import com.geuneul.domain.weather.Weather;

/**
 * 날씨 조회 응답 — 관측값 + 사람이 읽을 한 줄 라벨.
 *
 * available=false면 키 미설정/기상청 장애/격자 결측으로 날씨를 못 붙인 것(호출부는 날씨 없이 동작).
 */
public record WeatherResponse(
        boolean available,
        Double temperatureC,
        Integer humidityPct,
        Double rain1hMm,
        PrecipitationType precipitation,
        String label,
        String observedAt
) {

    public static WeatherResponse unavailable() {
        return new WeatherResponse(false, null, null, null, null, "날씨 정보 없음", null);
    }

    public static WeatherResponse of(Weather w) {
        return new WeatherResponse(
                true, w.temperatureC(), w.humidityPct(), w.rain1hMm(),
                w.precipitation(), label(w), w.observedAt());
    }

    private static String label(Weather w) {
        StringBuilder sb = new StringBuilder("지금");
        if (w.temperatureC() != null) {
            sb.append(' ').append(Math.round(w.temperatureC())).append("°C");
        }
        String rain = rainLabel(w.precipitation());
        if (rain != null) {
            sb.append(", ").append(rain);
        } else if (w.temperatureC() != null && w.temperatureC() >= 31) {
            sb.append(", 무더움");
        }
        return sb.length() == 2 ? "날씨 정보 없음" : sb.toString();
    }

    private static String rainLabel(PrecipitationType pty) {
        if (pty == null) {
            return null;
        }
        return switch (pty) {
            case RAIN, DRIZZLE -> "비";
            case RAIN_SNOW, DRIZZLE_SNOW -> "비/눈";
            case SNOW, SNOW_FLURRY -> "눈";
            case NONE -> null;
        };
    }
}
