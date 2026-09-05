package com.shorty.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shorty.support.InfrastructureIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "it.rate-limit.guest-create-per-minute=5",
            "it.rate-limit.unlock-per-minute=3",
            "it.rate-limit.redirect-per-minute=5",
            "it.rate-limit.auth-per-minute=5",
            "it.rate-limit.jwt-create-per-minute=5",
            "it.rate-limit.api-key-write-per-minute=5"
        })
@AutoConfigureMockMvc
class RateLimitIT extends InfrastructureIT {

    @Autowired
    MockMvc mvc;

    @Test
    void guestCreateBurstReturns429() throws Exception {
        String ip = uniqueIp();
        String body = "{\"destinationUrl\":\"https://example.com/rl\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/urls")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/urls")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("rate_limited"));
    }

    @Test
    void unlockBurstIsPerIpAndCode() throws Exception {
        String ip = uniqueIp();
        String code = "rl" + Long.toHexString(System.nanoTime()).substring(0, 5);
        mvc.perform(post("/api/v1/urls")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://example.com/pw","customCode":"%s","password":"s3cret"}
                                """.formatted(code)))
                .andExpect(status().isCreated());
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/urls/" + code + "/unlock")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"nope\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/urls/" + code + "/unlock")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"nope\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void redirectBurstReturns429() throws Exception {
        String ip = uniqueIp();
        String code = "rd" + Long.toHexString(System.nanoTime()).substring(0, 5);
        mvc.perform(post("/api/v1/urls")
                        .header("X-Forwarded-For", uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://example.com/r","customCode":"%s"}
                                """.formatted(code)))
                .andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/s/" + code).header("X-Forwarded-For", ip)).andExpect(status().isFound());
        }
        mvc.perform(get("/s/" + code).header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private static String uniqueIp() {
        long n = System.nanoTime();
        return "203.0." + ((n >> 8) & 0xff) + "." + (n & 0xff);
    }
}
