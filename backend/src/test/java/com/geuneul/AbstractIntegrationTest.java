package com.geuneul;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합테스트 공통 베이스 — 실 PostGIS로 검증(H2가 놓치는 공간 dialect·타입 drift를 잡는다).
 * (Redis는 메인 코드에서 아직 미사용 — P3 캐시 도입 시 여기에 컨테이너를 다시 추가한다.)
 *
 * ⚠️ 싱글턴 컨테이너 패턴 — @Container를 일부러 쓰지 않는다 (TS-002).
 * @Container(static)는 "각 테스트 클래스 종료 시 컨테이너 중지"인데, Spring TestContext는
 * 컨텍스트를 JVM 전체에 캐싱한다. 그 결과 두 번째 IT 클래스부터 캐시된 컨텍스트가
 * 죽은(또는 재시작되어 포트가 바뀐) 컨테이너로 접속 → ConnectException.
 * 수동 start() 싱글턴은 JVM 수명과 함께 살고, 종료 시 Testcontainers Ryuk이 정리한다.
 *
 * Docker 데몬이 없으면 @Testcontainers(disabledWithoutDocker)로 자동 skip —
 * static 블록도 Docker 가용 시에만 start하므로 오프라인 로컬 빌드가 깨지지 않는다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.context.annotation.Import(com.geuneul.domain.ingest.geocode.FakeGeocodingConfig.class)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("geuneul");

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }
}
