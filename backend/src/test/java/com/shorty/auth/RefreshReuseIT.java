package com.shorty.auth;

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
class RefreshReuseIT extends HighLimitIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void rotatedRefreshReuseRevokesEverySession() throws Exception {
        String email = "reuse" + System.nanoTime() + "@example.com";
        JsonNode sessionA = register(email);
        String refreshA = sessionA.get("refreshToken").asText();

        JsonNode sessionB = login(email);
        String refreshB = sessionB.get("refreshToken").asText();

        JsonNode rotatedA = refreshOk(refreshA);
        String refreshA2 = rotatedA.get("refreshToken").asText();

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshA + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_reuse"));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshB + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshA2 + "\"}"))
                .andExpect(status().isUnauthorized());

        JsonNode other = register("other" + System.nanoTime() + "@example.com");
        refreshOk(other.get("refreshToken").asText());
    }

    @Test
    void unknownRefreshDoesNotRevokeOtherSessions() throws Exception {
        JsonNode session = register("unk" + System.nanoTime() + "@example.com");
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"totally-unknown-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_refresh"));
        refreshOk(session.get("refreshToken").asText());
    }

    private JsonNode register(String email) throws Exception {
        String json = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password12"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(json);
    }

    private JsonNode login(String email) throws Exception {
        String json = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password12"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(json);
    }

    private JsonNode refreshOk(String token) throws Exception {
        String json = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return mapper.readTree(json);
    }
}
