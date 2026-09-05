package com.shorty.url;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UrlCacheEntry(
        Long urlId,
        String shortCode,
        String destinationUrl,
        boolean active,
        Instant expiresAt,
        Integer maxClicks,
        long clickCount,
        boolean passwordProtected,
        boolean publicClickCount,
        Instant createdAt
) {
    public boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean maxClicksReached() {
        return maxClicks != null && clickCount >= maxClicks;
    }
}
