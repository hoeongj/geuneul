package com.geuneul.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientConfigTest {

    @Test
    @DisplayName("RestClient.Builder 빈은 호출마다 새 builder를 만든다")
    void builderIsNotShared() {
        RestClientConfig config = new RestClientConfig();

        assertThat(config.restClientBuilder()).isNotSameAs(config.restClientBuilder());
        assertThat(config.restClientBuilder().build()).isNotNull();
    }
}
