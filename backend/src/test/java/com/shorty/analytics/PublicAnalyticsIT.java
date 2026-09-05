package com.shorty.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shorty.support.HighLimitIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PublicAnalyticsIT extends HighLimitIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void publicGetOmitsBreakdownAndClickCountUnlessFlagged() throws Exception {
        String privateCode = "pa" + Long.toHexString(System.nanoTime()).substring(0, 5);
        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://example.com/priv","customCode":"%s"}
                                """.formatted(privateCode)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/urls/" + privateCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").doesNotExist())
                .andExpect(jsonPath("$.countries").doesNotExist())
                .andExpect(jsonPath("$.browsers").doesNotExist())
                .andExpect(jsonPath("$.series").doesNotExist());

        mvc.perform(get("/api/v1/urls/" + privateCode + "/analytics")).andExpect(status().isUnauthorized());

        String publicCode = "pb" + Long.toHexString(System.nanoTime()).substring(0, 5);
        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://example.com/pub","customCode":"%s","publicClickCount":true}
                                """.formatted(publicCode)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/urls/" + publicCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clickCount").value(0))
                .andExpect(jsonPath("$.countries").doesNotExist());

        String email = "own" + System.nanoTime() + "@example.com";
        String access = mapper.readTree(mvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"email":"%s","password":"password12"}
                                        """.formatted(email)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("accessToken")
                .asText();

        String owned = "pc" + Long.toHexString(System.nanoTime()).substring(0, 5);
        mvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://example.com/own","customCode":"%s"}
                                """.formatted(owned)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/urls/" + owned + "/analytics").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0))
                .andExpect(jsonPath("$.countries").isArray());
    }
}
