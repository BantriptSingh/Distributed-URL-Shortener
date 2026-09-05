package com.shorty.url;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CreateUrlRequest(
        @NotBlank @JsonAlias("longUrl") String destinationUrl,
        @JsonAlias("customAlias") String customCode,
        Instant expiresAt,
        @Positive Integer maxClicks,
        @Size(max = 20) List<String> tags,
        Boolean publicClickCount
) {}
