package com.geuneul.domain.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushEndpointValidatorTest {

    @Test
    @DisplayName("FCM Web Push endpoint는 통과한다")
    void fcmEndpointPasses() {
        PushEndpointValidator.requireValid("https://fcm.googleapis.com/fcm/send/abc123");
    }

    @Test
    @DisplayName("http endpoint는 400")
    void httpEndpointRejected() {
        assertThatThrownBy(() -> PushEndpointValidator.requireValid("http://fcm.googleapis.com/fcm/send/abc123"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    @DisplayName("localhost endpoint는 400")
    void localhostRejected() {
        assertThatThrownBy(() -> PushEndpointValidator.requireValid("https://localhost/push"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    @DisplayName("IP literal endpoint는 400")
    void ipLiteralsRejected() {
        assertThatThrownBy(() -> PushEndpointValidator.requireValid("https://169.254.169.254/latest"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> PushEndpointValidator.requireValid("https://10.0.0.1/push"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> PushEndpointValidator.requireValid("https://127.0.0.1/push"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    @DisplayName("2048자를 넘으면 400")
    void tooLongRejected() {
        assertThatThrownBy(() -> PushEndpointValidator.requireValid("https://fcm.googleapis.com/" + "a".repeat(2048)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }
}
