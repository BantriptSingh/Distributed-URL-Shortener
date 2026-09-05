package com.shorty.url;

import java.time.Instant;
import java.util.List;

public record PatchUrlRequest(
        String destinationUrl,
        Instant expiresAt,
        Integer maxClicks,
        List<String> tags,
        Boolean isActive,
        String password,
        Boolean publicClickCount,
        Boolean hidePreview
) {}
