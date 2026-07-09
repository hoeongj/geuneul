package com.geuneul.global.observability;

import com.geuneul.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관측성(P4, ADR-0014) — {@code MANAGEMENT_EXPOSURE=health,info,prometheus}로 옵트인했을 때(로컬
 * docker-compose observability 프로필이 실제로 쓰는 값)의 동작을 검증한다. 이 프로퍼티가 켜져야만
 * 로컬 Prometheus가 스크레이프할 수 있으므로, "옵트인하면 실제로 동작한다"는 계약도 기본값이 안전하다는
 * 계약(ActuatorExposureIT)만큼 중요하다 — 둘 다 없으면 로컬 관측 스택이 조용히 죽어도 못 알아챈다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "management.endpoints.web.exposure.include=health,info,prometheus")
class ActuatorPrometheusOptInIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("옵트인 시 /actuator/prometheus는 200 + Prometheus 텍스트 노출 포맷을 반환한다")
    void prometheusIsExposedWhenExplicitlyOptedIn() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/plain")));
    }

    @Test
    @DisplayName("옵트인해도 health는 계속 공개(회귀 방지 — 옵트인이 기존 공개 엔드포인트를 안 건드림)")
    void healthStillWorksWhenOptedIn() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
