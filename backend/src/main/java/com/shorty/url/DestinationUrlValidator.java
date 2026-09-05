package com.shorty.url;

import com.shorty.api.ApiException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * M1 stub: protocol + URI syntax only. DNS/SSRF and reputation denylist land in M4.
 */
@Component
public class DestinationUrlValidator {

    private static final Set<String> ALLOWED = Set.of("http", "https");

    public String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "destinationUrl is required");
        }
        String trimmed = raw.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "destinationUrl is not a valid URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(scheme)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid_url",
                    "Only http and https URLs are allowed");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "destinationUrl must include a host");
        }
        return trimmed;
    }
}
