package com.geuneul.domain.route;

import com.geuneul.domain.report.ExternalApiRateLimiter;
import com.geuneul.domain.report.ProxyClientResolver;
import com.geuneul.domain.route.dto.RouteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

/**
 * 경로(B2, ADR-0019) — 화장실 포함 경로. 공개(로그인 불필요, 공개 커먼스). 그늘/비 경로 오버레이(shadeSpots)는 RouteService에 구현됨(N8, ADR-0024).
 */
@Tag(name = "Routes", description = "화장실 포함 경로 — 출발→경유 화장실→도착")
@RestController
public class RouteController {

    static final int PER_MINUTE = 20;

    private final RouteService routeService;
    private final ExternalApiRateLimiter rateLimiter;
    private final ProxyClientResolver clientResolver;

    public RouteController(RouteService routeService, ExternalApiRateLimiter rateLimiter,
                           ProxyClientResolver clientResolver) {
        this.routeService = routeService;
        this.rateLimiter = rateLimiter;
        this.clientResolver = clientResolver;
    }

    @Operation(summary = "화장실 포함 경로",
            description = "출발·도착 사이 우회 최소 화장실 1곳을 경유지로 끼운 경로. mode=straight(직선 MVP)|road(외부 API).")
    @GetMapping("/routes/toilet")
    public RouteResponse toiletRoute(@RequestParam double fromLat, @RequestParam double fromLng,
                                     @RequestParam double toLat, @RequestParam double toLng,
                                     HttpServletRequest http) {
        requireRateLimit(http);
        requireKorea(fromLat, fromLng);
        requireKorea(toLat, toLng);
        return routeService.toiletRoute(fromLat, fromLng, toLat, toLng);
    }

    @Operation(summary = "그늘 경유 경로",
            description = "출발·도착 사이 우회 최소 쿨링쉼터/도서관/지하상가 1곳을 경유지로 끼운 경로(C4). /routes/toilet과 동일 스키마.")
    @GetMapping("/routes/shade")
    public RouteResponse shadeRoute(@RequestParam double fromLat, @RequestParam double fromLng,
                                    @RequestParam double toLat, @RequestParam double toLng,
                                    HttpServletRequest http) {
        requireRateLimit(http);
        requireKorea(fromLat, fromLng);
        requireKorea(toLat, toLng);
        return routeService.shadeRoute(fromLat, fromLng, toLat, toLng);
    }

    /** 국내 좌표 범위 방어(PlaceController와 동일 규약) — 뒤집힌 lat/lng·해외 좌표를 400으로 막는다. */
    private static void requireKorea(double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || lat < 33 || lat > 39 || lng < 124 || lng > 132) {
            throw new ResponseStatusException(BAD_REQUEST, "좌표가 국내 범위를 벗어났습니다: " + lat + "," + lng);
        }
    }

    private void requireRateLimit(HttpServletRequest http) {
        if (!rateLimiter.tryAcquire("routes", clientResolver.resolve(http), PER_MINUTE)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "경로 요청이 너무 잦아요. 잠시 후 다시 시도해 주세요.");
        }
    }
}
