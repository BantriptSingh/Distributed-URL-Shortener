package com.shorty.analytics;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shorty.api.ApiException;
import com.shorty.config.AppProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LiveClickHubCapTest {

    @Test
    void publicLiveStreamIsCapped() {
        var hub = new LiveClickHub(new AppProperties(
                "http://localhost:8080",
                "http://localhost:5173",
                "http://localhost:5173",
                new AppProperties.Cache(Duration.ofHours(24), Duration.ofSeconds(30)),
                new AppProperties.ShortCode(7, 5),
                null,
                2));
        hub.subscribe();
        hub.subscribe();
        assertThatThrownBy(hub::subscribe)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    org.assertj.core.api.Assertions.assertThat(api.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    org.assertj.core.api.Assertions.assertThat(api.getCode()).isEqualTo("rate_limited");
                });
    }
}
