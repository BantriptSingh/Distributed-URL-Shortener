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
        ShortCode shortCode,
        RateLimit rateLimit,
        int liveSseMax
) {
    public AppProperties {
        if (rateLimit == null) {
            rateLimit = RateLimit.defaults();
        }
        if (liveSseMax <= 0) {
            liveSseMax = 500;
        }
    }

    public List<String> corsOrigins() {
        return Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public record Cache(Duration positiveTtl, Duration negativeTtl) {}

    public record ShortCode(int length, int maxRetries) {}

    public record RateLimit(
            int guestCreatePerMinute,
            int jwtCreatePerMinute,
            int apiKeyWritePerMinute,
            int redirectPerMinute,
            int authPerMinute,
            int unlockPerMinute
    ) {
        static RateLimit defaults() {
            return new RateLimit(30, 120, 300, 600, 10, 10);
        }
    }
}
