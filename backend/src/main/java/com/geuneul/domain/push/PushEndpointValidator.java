package com.geuneul.domain.push;

import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * Web Push endpoint 저장 전 SSRF 방어. 실제 전송은 비동기지만, 구독 저장 단계에서 위험한 endpoint를
 * 차단해 내부망/로컬 주소로의 후속 HTTP 요청을 만들지 않는다.
 */
final class PushEndpointValidator {

    static final int MAX_ENDPOINT_LENGTH = 2048;

    private PushEndpointValidator() {
    }

    static void requireValid(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > MAX_ENDPOINT_LENGTH) {
            throw new ResponseStatusException(BAD_REQUEST, "push endpoint가 올바르지 않습니다");
        }
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(BAD_REQUEST, "push endpoint URL 형식이 올바르지 않습니다");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ResponseStatusException(BAD_REQUEST, "push endpoint는 https URL이어야 합니다");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || isLocalhost(host) || isIpLiteral(host)) {
            throw new ResponseStatusException(BAD_REQUEST, "허용되지 않는 push endpoint host입니다");
        }
    }

    private static boolean isLocalhost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized) || "localhost.".equals(normalized);
    }

    private static boolean isIpLiteral(String host) {
        return isIpv4Literal(host) || host.indexOf(':') >= 0;
    }

    private static boolean isIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                return false;
            }
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }
}
