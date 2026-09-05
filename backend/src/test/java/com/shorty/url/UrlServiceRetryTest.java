package com.shorty.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shorty.api.ApiException;
import com.shorty.click.ClickCountStore;
import com.shorty.click.ClickRepository;
import com.shorty.config.AppProperties;
import com.shorty.id.ShortCodeGenerator;
import com.shorty.id.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

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
                clickRepository);
        org.mockito.Mockito.lenient().when(clickCounts.getOrLoad(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
    }

    @Test
    void retriesOnCollisionThenSucceeds() {
        when(codes.next(7)).thenReturn("aaaaaaa", "bbbbbbb");
        when(urls.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("dup"))
                .thenAnswer(inv -> inv.getArgument(0));

        var created = service.create(new CreateUrlRequest("https://example.com/x", null, null, null, null, false));

        assertThat(created.shortCode()).isEqualTo("bbbbbbb");
        verify(codes, times(2)).next(7);
        verify(cache).putPositive(any());
    }

    @Test
    void exhaustsRetriesAndReturns500() {
        when(codes.next(anyInt())).thenReturn("ccccccc");
        when(urls.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(
                        () -> service.create(
                                new CreateUrlRequest("https://example.com/x", null, null, null, null, false)))
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

        assertThatThrownBy(() -> service.create(
                        new CreateUrlRequest("https://example.com/x", "MyLink", null, null, null, false)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void rejectsNonHttpProtocols() {
        assertThatThrownBy(() -> service.create(
                        new CreateUrlRequest("javascript:alert(1)", null, Instant.now(), null, null, false)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("invalid_url"));
    }
}
