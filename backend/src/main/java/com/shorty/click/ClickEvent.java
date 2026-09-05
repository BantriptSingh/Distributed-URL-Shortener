package com.shorty.click;

import com.shorty.click.UserAgentClassifier.Classification;
import java.time.Instant;

public record ClickEvent(
        long urlId,
        String shortCode,
        String ip,
        String userAgent,
        String referrer,
        String country,
        Instant timestamp,
        Classification ua
) {}
