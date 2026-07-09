package com.geuneul.domain.alert;

import com.geuneul.domain.alert.dto.SurgeInfo;
import com.geuneul.global.web.ApiRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 실시간 제보 급증 알림 API (ADR-0016). 둘 다 permitAll(공개 커먼스 — 알림은 로그인 불필요).
 * <ul>
 *   <li>{@code GET /alerts/surge?bounds=} — 뷰포트 내 급증 장소 목록(폴백/초기 스냅샷, 시공간 SQL 간판)</li>
 *   <li>{@code GET /alerts/stream} — SSE 실시간 푸시(LISTEN/NOTIFY로 전 인스턴스 전파된 급증 이벤트)</li>
 * </ul>
 */
@Tag(name = "Alerts", description = "실시간 제보 급증 알림 — 뷰포트 스냅샷(polling) + SSE 스트림")
@RestController
public class AlertController {

    static final int MAX_LIMIT = 200;

    private final ReportSurgeService surgeService;
    private final SurgeEmitterRegistry emitterRegistry;

    public AlertController(ReportSurgeService surgeService, SurgeEmitterRegistry emitterRegistry) {
        this.surgeService = surgeService;
        this.emitterRegistry = emitterRegistry;
    }

    @Operation(summary = "뷰포트 내 급증 장소(스냅샷/폴백)",
            description = "bounds=west,south,east,north 안에서 최근 시간창에 유효 제보가 임계 이상 몰린 장소. "
                    + "SSE 미지원 환경·재연결 공백을 보완하는 폴백 겸 초기 스냅샷.")
    @GetMapping("/alerts/surge")
    public List<SurgeInfo> surge(
            @Parameter(description = "west,south,east,north (경도,위도,경도,위도)") @RequestParam String bounds,
            @Parameter(description = "최대 결과 수, 기본 50, 최대 200") @RequestParam(defaultValue = "50") int limit) {
        double[] box = parseBounds(bounds);
        int safeLimit = ApiRequests.clampLimit(limit, MAX_LIMIT);
        return surgeService.surgingInBounds(box[0], box[1], box[2], box[3], safeLimit);
    }

    @Operation(summary = "급증 알림 실시간 스트림(SSE)",
            description = "text/event-stream. 제보 급증이 감지되면 name=\"surge\" 이벤트로 SurgeInfo가 푸시된다. "
                    + "브라우저 EventSource가 끊기면 자동 재연결한다. 초기 상태는 GET /alerts/surge로 받는다.")
    @GetMapping(value = "/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return emitterRegistry.register();
    }

    /** "west,south,east,north" → double[4]. 잘못된 형식은 400(PlaceController와 동일 규약). */
    private static double[] parseBounds(String bounds) {
        if (bounds == null || bounds.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "bounds 형식: west,south,east,north");
        }
        String[] parts = bounds.split(",");
        if (parts.length != 4) {
            throw new ResponseStatusException(BAD_REQUEST, "bounds 형식: west,south,east,north");
        }
        try {
            double west = Double.parseDouble(parts[0].trim());
            double south = Double.parseDouble(parts[1].trim());
            double east = Double.parseDouble(parts[2].trim());
            double north = Double.parseDouble(parts[3].trim());
            if (west >= east || south >= north) {
                throw new ResponseStatusException(BAD_REQUEST, "bounds가 뒤집혔습니다 (west<east, south<north)");
            }
            return new double[]{west, south, east, north};
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(BAD_REQUEST, "bounds 숫자 파싱 실패: " + bounds);
        }
    }
}
