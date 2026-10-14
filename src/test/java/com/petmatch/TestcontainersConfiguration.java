package com.petmatch;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts one throw-away PostGIS database for the whole test run and points the Spring
 * datasource at it, so tests never touch the development database.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // One container shared by every Spring context in this test run. The "postgis" image is
    // declared compatible with "postgres" so Testcontainers accepts it.
    private static final PostgreSQLContainer POSTGIS = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGIS.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgisContainer() {
        return POSTGIS;
    }
}
