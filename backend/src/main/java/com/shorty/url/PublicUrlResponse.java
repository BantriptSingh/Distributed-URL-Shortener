package com.shorty.url;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicUrlResponse(
        String shortCode,
        Instant createdAt,
        boolean isActive,
        Instant expiresAt,
        Long clickCount
) {}
