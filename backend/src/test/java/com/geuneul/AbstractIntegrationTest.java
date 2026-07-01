package com.geuneul;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합테스트 공통 베이스 — 실 PostGIS + Redis 컨테이너 (H2가 놓치는 공간 dialect를 실 DB로 검증).
 * static 컨테이너라 같은 JVM의 모든 IT가 재사용한다(클래스마다 재기동 없음 → CI 시간 절약).
 * Docker 데몬이 없으면 자동 skip → 오프라인 로컬 빌드도 green.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("geuneul");

    @Container
    @ServiceConnection
    static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7-alpine"));
}
