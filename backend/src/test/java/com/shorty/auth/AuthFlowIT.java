package com.shorty.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class AuthFlowIT extends HighLimitIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void registerLoginMeClaimAnalyticsAndHidePreview() throws Exception {
        String email = "user" + System.nanoTime() + "@example.com";
        String registerJson = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password12"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode session = mapper.readTree(registerJson);
        String access = session.get("accessToken").asText();
        String refresh = session.get("refreshToken").asText();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        String guestJson = mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://example.com/guest","customCode":"claimzz"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimToken").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String claimToken = mapper.readTree(guestJson).get("claimToken").asText();

        mvc.perform(post("/api/v1/urls/claimzz/claim")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"claimToken\":\"" + claimToken + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/urls/claimzz/analytics").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0));

        mvc.perform(get("/api/v1/urls/claimzz/analytics")).andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/v1/urls/claimzz")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidePreview\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationUrl").value("https://example.com/guest"));

        mvc.perform(get("/api/v1/urls/claimzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationUrl").doesNotExist());

        String keyJson = mvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopes\":[\"read\",\"write\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String apiKey = mapper.readTree(keyJson).get("key").asText();

        mvc.perform(get("/api/v1/urls").header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].shortCode").value("claimzz"));

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        mvc.perform(get("/api/v1/urls/claimzz/qr.png")).andExpect(status().isOk());

        mvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://example.com/secret","customCode":"pwlink1","password":"s3cret"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/s/pwlink1"))
                .andExpect(status().isFound())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Location")).contains("/unlock/pwlink1"));

        String unlockJson = mvc.perform(post("/api/v1/urls/pwlink1/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"s3cret\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String unlock = mapper.readTree(unlockJson).get("unlockToken").asText();

        mvc.perform(get("/s/pwlink1").param("u", unlock))
                .andExpect(status().isFound())
                .andExpect(result ->
                        assertThat(result.getResponse().getHeader("Location")).isEqualTo("https://example.com/secret"));
    }
}
