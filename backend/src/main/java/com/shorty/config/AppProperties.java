package com.shorty.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        String frontendBaseUrl,
        String corsAllowedOrigins,
        Cache cache,
        ShortCode shortCode
) {
    public List<String> corsOrigins() {
        return Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public record Cache(Duration positiveTtl, Duration negativeTtl) {}

    public record ShortCode(int length, int maxRetries) {}
}
