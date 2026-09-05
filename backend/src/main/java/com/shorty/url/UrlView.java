package com.shorty.url;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UrlView(
        String shortCode,
        String shortUrl,
        String destinationUrl,
        Instant createdAt,
        Instant updatedAt,
        Boolean isActive,
        Instant expiresAt,
        Integer maxClicks,
        Long clickCount,
        List<String> tags,
        Boolean publicClickCount,
        Boolean hidePreview,
        Boolean passwordProtected,
        Boolean customAlias,
        Boolean owned
) {}
