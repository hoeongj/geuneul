package com.geuneul;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 전체 스프링 컨텍스트가 실 PostGIS + Redis 컨테이너에서 부팅되는지 검증한다.
 * 부팅 과정에서 Flyway 마이그레이션(PostGIS 확장 활성화 + GiST 인덱스 생성)이 실행되므로,
 * 이 테스트가 green이면 공간 스택 전체(PostGIS·Flyway·JPA·Redis 연결)가 살아있다는 뜻.
 * Docker 데몬이 없으면 자동 skip(disabledWithoutDocker) → 오프라인 로컬 빌드도 green.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class GeuneulApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("geuneul");

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(
            DockerImageName.parse("redis:7-alpine"));

    @Test
    void contextLoads() {
        // 컨텍스트 로드 + Flyway 마이그레이션 성공 자체가 검증. 별도 assertion 불필요.
    }
}
