package com.shorty.url;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class RedirectServiceXaddFailureTest {

    @Mock
    UrlService urls;

    @Mock
    ClickCountStore clickCounts;

    @Mock
    ClickEventPublisher publisher;

    @Mock
    UnlockTokenService unlocks;

    @InjectMocks
    RedirectService redirects;

    @Test
    void xaddFailureStillRedirectsAndDecrements() {
        var entry = new UrlCacheEntry(
                1L,
                null,
                "abc1234",
                "https://example.com",
                true,
                null,
                10,
                0,
                false,
                false,
                false,
                Instant.now());
        when(urls.requireActive("abc1234")).thenReturn(entry);
        when(clickCounts.increment("abc1234")).thenReturn(1L);
        when(publisher.publish(any())).thenReturn(false);

        RedirectService service = new RedirectService(urls, clickCounts, publisher, new HeaderGeoResolver(), unlocks);
        String loc = service.locationFor("abc1234", new MockHttpServletRequest());

        org.assertj.core.api.Assertions.assertThat(loc).isEqualTo("https://example.com");
        verify(clickCounts).decrement("abc1234");
    }
}
