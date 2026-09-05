package com.shorty.url;

import com.shorty.api.ApiException;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class CustomAliasValidator {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9-]{3,30}$");
    private static final Set<String> RESERVED = Set.of(
            "api",
            "admin",
            "s",
            "login",
            "auth",
            "swagger-ui",
            "actuator",
            "docs",
            "static");

    public String validateAndNormalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_alias", "customCode is required when provided");
        }
        String normalized = ShortCodes.normalize(raw);
        if (!PATTERN.matcher(normalized).matches()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "invalid_alias",
                    "customCode must be 3–30 characters: letters, digits, hyphen");
        }
        if (RESERVED.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "reserved_alias", "That custom code is reserved");
        }
        return normalized;
    }
}
