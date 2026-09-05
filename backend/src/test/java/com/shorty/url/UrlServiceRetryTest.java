package com.shorty.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shorty.api.ApiException;
import com.shorty.auth.GuestClaimTokenRepository;
import com.shorty.click.ClickCountStore;
import com.shorty.click.ClickRepository;
import com.shorty.config.AppProperties;
import com.shorty.id.ShortCodeGenerator;
import com.shorty.id.SnowflakeIdGenerator;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UrlServiceRetryTest {

    @Mock
    private UrlRepository urls;

    @Mock
    private UrlCache cache;

    @Mock
    private ShortCodeGenerator codes;

    @Mock
    private ClickCountStore clickCounts;

    @Mock
    private ClickRepository clickRepository;

    @Mock
    private GuestClaimTokenRepository claims;

    @Mock
    private PasswordEncoder passwords;

    private UrlService service;

    @BeforeEach
    void setUp() {
        var props = new AppProperties(
                "http://localhost:8080",
                "http://localhost:5173",
                "http://localhost:5173",
                new AppProperties.Cache(Duration.ofHours(24), Duration.ofSeconds(30)),
                new AppProperties.ShortCode(7, 5));
        service = new UrlService(
                urls,
                cache,
                codes,
                new SnowflakeIdGenerator(1),
                new DestinationUrlValidator(),
                new CustomAliasValidator(),
                props,
                clickCounts,
                clickRepository,
                claims,
                passwords,
                Duration.ofDays(7));
        lenient().when(clickCounts.getOrLoad(any(), any())).thenReturn(0L);
    }

    @Test
    void retriesOnCollisionThenSucceeds() {
        when(codes.next(7)).thenReturn("aaaaaaa", "bbbbbbb");
        when(urls.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("dup"))
                .thenAnswer(inv -> inv.getArgument(0));

        var created = service.create(req("https://example.com/x", null), 1L);

        assertThat(created.shortCode()).isEqualTo("bbbbbbb");
        verify(codes, times(2)).next(7);
        verify(cache).putPositive(any());
    }

    @Test
    void exhaustsRetriesAndReturns500() {
        when(codes.next(anyInt())).thenReturn("ccccccc");
        when(urls.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.create(req("https://example.com/x", null), 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(api.getCode()).isEqualTo("short_code_exhausted");
                });
        verify(urls, times(5)).saveAndFlush(any());
    }

    @Test
    void customAliasConflictIs409() {
        when(urls.existsByShortCodeIgnoreCase("mylink")).thenReturn(true);

        assertThatThrownBy(() -> service.create(req("https://example.com/x", "MyLink"), 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsNonHttpProtocols() {
        assertThatThrownBy(() -> service.create(req("javascript:alert(1)", null), 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("invalid_url"));
    }

    private static CreateUrlRequest req(String url, String alias) {
        return new CreateUrlRequest(url, alias, null, null, null, false, false, null);
    }
}
