package com.shorty.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shorty.click.ClickRepository;
import com.shorty.support.HighLimitIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UrlFlowIT extends HighLimitIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    UrlRepository urlRepository;

    @Autowired
    ClickRepository clickRepository;

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

        mvc.perform(get("/s/MYLINK")
                        .header("CF-IPCountry", "DE")
                        .header("User-Agent", "Mozilla/5.0 Chrome/120.0.0.0")
                        .header("Referer", "https://news.example"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/docs"));

        mvc.perform(get("/api/v1/urls/mylink"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("mylink"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.destinationUrl").value("https://example.com/docs"))
                .andExpect(jsonPath("$.clickCount").doesNotExist());

        assertThat(redisTemplate.hasKey("url:mylink")).isTrue();

        var url = urlRepository.findByShortCodeIgnoreCase("mylink").orElseThrow();
        waitForClicks(url.getId(), 1);
        var click = clickRepository.findAll().stream()
                .filter(c -> c.getUrlId().equals(url.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(click.getCountry()).isEqualTo("DE");
        assertThat(click.getIpHash()).hasSize(64);
        assertThat(click.getIpHash()).doesNotContain("127.0.0.1");
        assertThat(click.getBrowser()).isEqualTo("chrome");
        assertThat(click.isBot()).isFalse();

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

    @Test
    void privateAndMetadataUrlsRejected() throws Exception {
        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"http://127.0.0.1/"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"http://10.0.0.1/"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"http://169.254.169.254/latest/meta-data"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://evil.blocked.test/phish"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("blocked_url"));
    }

    private void waitForClicks(long urlId, int expected) throws InterruptedException {
        for (int i = 0; i < 80; i++) {
            if (clickRepository.countByUrlId(urlId) >= expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("timed out waiting for " + expected + " clicks on url " + urlId);
    }
}
