package com.geuneul.domain.ingest.openapi;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IT용 페이크 도서관 오픈API — 실제 data.go.kr 호출 없이 페이지네이션 경로를 결정적으로 검증한다
 * (FakeGeocodingConfig와 동일 패턴). 테스트가 페이지별 응답을 심고, 심지 않은 페이지는 빈 페이지(종료 신호).
 */
@TestConfiguration
public class FakeLibraryApiConfig {

    public static class FakeLibraryApiClient implements PublicLibraryApiClient {

        private final Map<Integer, LibraryPage> pages = new ConcurrentHashMap<>();

        public void setPage(int pageNo, LibraryPage page) {
            pages.put(pageNo, page);
        }

        public void reset() {
            pages.clear();
        }

        @Override
        public LibraryPage fetchPage(int pageNo, int numOfRows) {
            return pages.getOrDefault(pageNo, LibraryPage.empty());
        }
    }

    @Bean
    @Primary
    public FakeLibraryApiClient fakeLibraryApiClient() {
        return new FakeLibraryApiClient();
    }
}
