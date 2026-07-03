package com.geuneul.domain.report;

import com.geuneul.domain.report.dto.ReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨트롤러 검증·리밋 분기 단위 테스트 (MockMvc, DB 불필요) —
 * 저장·만료 필터링의 실제 동작은 ReportFlowIT에서 실 PostGIS로 검증한다.
 */
@WebMvcTest(ReportController.class)
class ReportControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReportService reportService;

    @MockitoBean
    ReportRateLimiter rateLimiter;

    @BeforeEach
    void allowByDefault() {
        given(rateLimiter.tryAcquire(anyString())).willReturn(true);
    }

    private static ReportResponse sample() {
        return new ReportResponse(1L, 1L, "COOL", "시원해요", null, true,
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(3));
    }

    @Test
    @DisplayName("정상 제보는 201 Created + 본문을 돌려준다")
    void createReturns201() throws Exception {
        given(reportService.create(any())).willReturn(sample());

        mvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":1,\"reportType\":\"COOL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportType").value("COOL"))
                .andExpect(jsonPath("$.reportTypeLabel").value("시원해요"));
    }

    @Test
    @DisplayName("reportType 누락이면 400")
    void missingTypeIs400() throws Exception {
        mvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("정의되지 않은 reportType 값이면 400")
    void unknownTypeIs400() throws Exception {
        mvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":1,\"reportType\":\"LAVA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("코멘트 120자 초과면 400")
    void tooLongCommentIs400() throws Exception {
        String longComment = "가".repeat(121);
        mvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":1,\"reportType\":\"COOL\",\"comment\":\"" + longComment + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("레이트리밋 초과면 429 — 서비스까지 내려가지 않는다")
    void rateLimitedIs429() throws Exception {
        given(rateLimiter.tryAcquire(anyString())).willReturn(false);

        mvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placeId\":1,\"reportType\":\"COOL\"}"))
                .andExpect(status().isTooManyRequests());

        then(reportService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("프록시 경유 요청은 X-Forwarded-For 최좌측 IP가 리밋 키가 된다")
    void xffLeftmostIsClientKey() throws Exception {
        given(reportService.create(any())).willReturn(sample());

        mvc.perform(post("/reports").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
                        .content("{\"placeId\":1,\"reportType\":\"COOL\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        then(rateLimiter).should().tryAcquire(key.capture());
        assertThat(key.getValue()).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("장소별 최근 제보 GET은 200 + 배열")
    void recentReportsOk() throws Exception {
        given(reportService.recentByPlace(anyLong())).willReturn(List.of(sample()));

        mvc.perform(get("/places/1/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportTypeLabel").value("시원해요"));
    }
}
