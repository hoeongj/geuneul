package com.geuneul.domain.weather;

import com.geuneul.domain.weather.dto.WeatherResponse;
import com.geuneul.global.web.ApiRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 날씨 조회 API — 좌표의 "지금" 관측값(기상청 초단기실황). Redis TTL 캐시 뒤에 있다.
 * survival_score 기온 보강의 데이터 소스이자, 프론트 상세/추천 화면의 날씨 배지용.
 */
@Tag(name = "Weather", description = "지금 날씨 — 기상청 초단기실황(격자 변환 + Redis TTL 캐시)")
@RestController
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Operation(summary = "지금 날씨 (좌표)",
            description = "위경도를 기상청 격자로 변환해 초단기실황을 조회한다. 키 미설정·장애 시 available=false.")
    @GetMapping("/weather")
    public WeatherResponse now(@RequestParam double lat, @RequestParam double lng) {
        ApiRequests.requireValidLatLng(lat, lng);
        return weatherService.getWeather(lat, lng)
                .map(WeatherResponse::of)
                .orElseGet(WeatherResponse::unavailable);
    }
}
