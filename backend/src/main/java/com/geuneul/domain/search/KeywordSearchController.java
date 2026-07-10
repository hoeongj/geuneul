package com.geuneul.domain.search;

import com.geuneul.domain.search.dto.PlaceSearchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 지정 장소 검색 API (N5) — 카카오 REST 키를 클라이언트에 노출하지 않도록 백엔드(BFF)가 대신 호출한다.
 * {@code GET /places/search}는 permitAll(SecurityConfig 기본) — 지도처럼 공개다. 우리 places(DB) 조회가 아니라
 * 지도를 이동시킬 목적지 좌표를 돌려주는 외부 검색 프록시다(반경 마커는 이동 후 bounds 조회가 기존대로 채운다).
 *
 * <p>경로 우선순위: {@code /places/search}(리터럴)는 {@code /places/{id}}(변수, PlaceController)보다
 * 더 구체적이라 Spring PathPattern이 이 핸들러로 라우팅한다(리터럴 세그먼트가 캡처보다 우선).
 */
@Tag(name = "Search", description = "지정 장소 검색 — 카카오 키워드(BFF 프록시, 키 비노출)")
@RestController
public class KeywordSearchController {

    static final int DEFAULT_SIZE = 10;

    private final KakaoKeywordClient keywordClient;

    public KeywordSearchController(KakaoKeywordClient keywordClient) {
        this.keywordClient = keywordClient;
    }

    @Operation(summary = "지정 장소 검색(카카오 키워드)",
            description = "query로 장소를 검색한다. lat/lng를 주면 그 좌표 기준 거리순으로 정렬(내 주변 우선). "
                    + "선택 시 프론트가 지도를 결과 좌표로 이동한다. 실패/무결과는 빈 배열.")
    @GetMapping("/places/search")
    public List<PlaceSearchResult> search(
            @Parameter(description = "검색어(필수, 공백 불가)") @RequestParam String query,
            @Parameter(description = "위치 바이어스 위도(선택)") @RequestParam(required = false) Double lat,
            @Parameter(description = "위치 바이어스 경도(선택)") @RequestParam(required = false) Double lng,
            @Parameter(description = "최대 결과 수, 기본 10, 최대 15") @RequestParam(defaultValue = "10") int size) {
        String q = query == null ? "" : query.strip();
        if (q.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "검색어를 입력해 주세요.");
        }
        int safeSize = size <= 0 ? DEFAULT_SIZE : size;
        return keywordClient.search(q, lat, lng, safeSize);
    }
}
