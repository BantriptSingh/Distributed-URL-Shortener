package com.shorty.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UrlFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shortener")
            .withUsername("shortener")
            .withPassword("changeme");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        registry.add("app.base-url", () -> "http://localhost:8080");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    UrlRepository urlRepository;

    @Test
    void createRedirectPublicGetAndCache() throws Exception {
        String body = """
                {"destinationUrl":"https://example.com/docs","customCode":"MyLink","tags":["docs"]}
                """;
        String createdJson = mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("mylink"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/s/mylink"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createdJson);
        assertThat(created.get("destinationUrl").asText()).isEqualTo("https://example.com/docs");

        mvc.perform(get("/s/MYLINK"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/docs"));

        mvc.perform(get("/api/v1/urls/mylink"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("mylink"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.clickCount").doesNotExist());

        assertThat(redisTemplate.hasKey("url:mylink")).isTrue();

        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"longUrl":"https://example.com/other","customCode":"mylink"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void missingCodeIs404AndNegativeCached() throws Exception {
        mvc.perform(get("/s/no-such-code-xyz")).andExpect(status().isNotFound());
        assertThat(redisTemplate.hasKey("url:miss:no-such-code-xyz")).isTrue();

        urlRepository.deleteAll();
        mvc.perform(get("/s/no-such-code-xyz")).andExpect(status().isNotFound());
    }

    @Test
    void secondResolveIsCacheHit() throws Exception {
        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://example.com/cached","customCode":"cached1"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/s/cached1")).andExpect(status().isFound());
        urlRepository.deleteAll();
        mvc.perform(get("/s/cached1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/cached"));
    }

    @Test
    void expiredLinkReturns410() throws Exception {
        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://example.com/old","customCode":"expired1","expiresAt":"2000-01-01T00:00:00Z"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/s/expired1")).andExpect(status().isGone());
    }

    @Test
    void javascriptUrlRejected() throws Exception {
        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"javascript:alert(1)"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_url"));
    }
}
