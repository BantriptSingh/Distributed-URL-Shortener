package com.shorty.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class UnlockTokenScopeIT extends HighLimitIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Test
    void unlockTokenDoesNotAppendToDestinationAndIsPerCode() throws Exception {
        String codeA = "ua" + Long.toHexString(System.nanoTime()).substring(0, 5);
        String codeB = "ub" + Long.toHexString(System.nanoTime()).substring(0, 5);

        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://example.com/a","customCode":"%s","password":"s3cret"}
                                """.formatted(codeA)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationUrl":"https://example.com/b","customCode":"%s","password":"s3cret"}
                                """.formatted(codeB)))
                .andExpect(status().isCreated());

        String unlockA = mapper.readTree(mvc.perform(post("/api/v1/urls/" + codeA + "/unlock")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"s3cret\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("unlockToken")
                .asText();

        mvc.perform(get("/s/" + codeA).param("u", unlockA))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/a"));

        String location = mvc.perform(get("/s/" + codeA).param("u", unlockA))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        assertThat(location).doesNotContain("u=").doesNotContain(unlockA);

        mvc.perform(get("/s/" + codeB).param("u", unlockA))
                .andExpect(status().isFound())
                .andExpect(result -> assertThat(result.getResponse().getHeader("Location")).contains("/unlock/" + codeB));
    }
}
