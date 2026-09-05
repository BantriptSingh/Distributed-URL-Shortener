package com.shorty.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.clicks")
public record ClickStreamProperties(
        String stream,
        String group,
        long maxlen,
        boolean consumerEnabled,
        Duration block,
        Duration claimIdle
) {
    public ClickStreamProperties {
        if (stream == null || stream.isBlank()) {
            stream = "clicks";
        }
        if (group == null || group.isBlank()) {
            group = "click-workers";
        }
        if (maxlen <= 0) {
            maxlen = 100_000;
        }
        if (block == null) {
            block = Duration.ofSeconds(2);
        }
        if (claimIdle == null) {
            claimIdle = Duration.ofSeconds(60);
        }
    }
}
