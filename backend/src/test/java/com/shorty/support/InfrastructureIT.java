package com.shorty.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class InfrastructureIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shortener")
            .withUsername("shortener")
            .withPassword("changeme");

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.data.redis.url", () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        registry.add("app.base-url", () -> "http://localhost:8080");
        registry.add("IP_HASH_SALT", () -> "test-ip-salt-not-a-secret");
        registry.add("JWT_SECRET", () -> "test-jwt-secret-must-be-32-chars-min");
        registry.add("JWT_REFRESH_SECRET", () -> "test-refresh-secret-32-chars-minx");
        registry.add("UNLOCK_TOKEN_SECRET", () -> "test-unlock-secret-32-chars-minx");
        registry.add("CLAIM_TOKEN_SECRET", () -> "test-claim-secret-32-chars-minxx");
        registry.add("DEV_EXPOSE_RESET_TOKEN", () -> "true");
    }
}
