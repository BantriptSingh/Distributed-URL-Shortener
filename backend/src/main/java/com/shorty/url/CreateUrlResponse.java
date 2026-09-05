package com.shorty.url;

import java.time.Instant;
import java.util.List;

public record CreateUrlResponse(
        String shortCode,
        String shortUrl,
        String destinationUrl,
        Instant createdAt,
        Instant expiresAt,
        Integer maxClicks,
        List<String> tags,
        boolean publicClickCount
) {}
