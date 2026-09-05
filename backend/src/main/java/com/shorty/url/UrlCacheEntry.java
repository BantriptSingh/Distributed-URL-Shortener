package com.shorty.url;

import java.time.Instant;

public record UrlCacheEntry(
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
