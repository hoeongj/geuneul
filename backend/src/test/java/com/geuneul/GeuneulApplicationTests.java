package com.geuneul;

import org.junit.jupiter.api.Test;

/**
 * 전체 스프링 컨텍스트가 실 PostGIS + Redis에서 부팅되는지 검증한다.
 * 부팅 중 Flyway 마이그레이션(PostGIS 확장 + GiST/geography 인덱스 + 시드)이 실행되므로,
 * 이 테스트가 green이면 공간 스택 전체가 살아있다는 뜻.
 */
class GeuneulApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // 컨텍스트 로드 + Flyway 마이그레이션 성공 자체가 검증.
    }
}
