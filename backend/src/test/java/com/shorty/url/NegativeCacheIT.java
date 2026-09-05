package com.shorty.url;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shorty.support.HighLimitIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class NegativeCacheIT extends HighLimitIT {

    @Autowired
    MockMvc mvc;

    @SpyBean
    UrlRepository urls;

    @Test
    void secondMissDoesNotHitDatabase() throws Exception {
        String code = "nc" + Long.toHexString(System.nanoTime()).substring(0, 6);
        mvc.perform(get("/s/" + code)).andExpect(status().isNotFound());
        mvc.perform(get("/s/" + code)).andExpect(status().isNotFound());
        verify(urls, times(1)).findByShortCodeIgnoreCase(code);
    }
}
