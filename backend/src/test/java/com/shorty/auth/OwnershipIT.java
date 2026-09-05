package com.shorty.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class OwnershipIT extends HighLimitIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void otherUserCannotMutateOrWatchForeignLink() throws Exception {
        String accessA = register("owna" + System.nanoTime() + "@example.com");
        String accessB = register("ownb" + System.nanoTime() + "@example.com");
        String code = "own" + Long.toHexString(System.nanoTime()).substring(0, 5);

        mvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + accessA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://example.com/owned","customCode":"%s"}
                                """.formatted(code)))
                .andExpect(status().isCreated());

        mvc.perform(patch("/api/v1/urls/" + code)
                        .header("Authorization", "Bearer " + accessB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hidePreview\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        mvc.perform(get("/api/v1/urls/" + code + "/analytics").header("Authorization", "Bearer " + accessB))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/urls/" + code + "/events").header("Authorization", "Bearer " + accessB))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/urls/" + code).header("Authorization", "Bearer " + accessB))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/urls/" + code + "/analytics").header("Authorization", "Bearer " + accessA))
                .andExpect(status().isOk());
    }

    private String register(String email) throws Exception {
        String json = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password12"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode node = mapper.readTree(json);
        return node.get("accessToken").asText();
    }
}
