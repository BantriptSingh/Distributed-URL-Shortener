package com.shorty.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shorty.auth.UnlockTokenService;
import com.shorty.click.ClickCountStore;
import com.shorty.click.ClickEventPublisher;
import com.shorty.geo.HeaderGeoResolver;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class RedirectServiceUnlockLocationTest {

    @Mock
    UrlService urls;

    @Mock
    ClickCountStore clickCounts;

    @Mock
    ClickEventPublisher publisher;

    @Mock
    UnlockTokenService unlocks;

    @Test
    void outboundLocationIsDestinationOnly() {
        var entry = new UrlCacheEntry(
                1L,
                null,
                "pwcode1",
                "https://example.com/dest",
                true,
                null,
                null,
                0,
                true,
                false,
                false,
                Instant.now());
        when(urls.requireActive("pwcode1")).thenReturn(entry);
        when(unlocks.valid("pwcode1", "tok.for.pwcode1")).thenReturn(true);
        when(clickCounts.increment("pwcode1")).thenReturn(1L);
        when(publisher.publish(any())).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("u", "tok.for.pwcode1");

        String loc = new RedirectService(urls, clickCounts, publisher, new HeaderGeoResolver(), unlocks)
                .locationFor("pwcode1", request);

        assertThat(loc).isEqualTo("https://example.com/dest");
        assertThat(loc).doesNotContain("u=");
        assertThat(loc).doesNotContain("tok.for.pwcode1");
        verify(unlocks).valid("pwcode1", "tok.for.pwcode1");
    }
}
