package com.shorty.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class HeaderGeoResolverTest {

    private final HeaderGeoResolver resolver = new HeaderGeoResolver();

    @Test
    void readsCloudflareHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("CF-IPCountry", "in");
        assertThat(resolver.resolve(req)).isEqualTo("IN");
    }

    @Test
    void unknownWithoutHeaders() {
        assertThat(resolver.resolve(new MockHttpServletRequest())).isEqualTo("ZZ");
    }
}
