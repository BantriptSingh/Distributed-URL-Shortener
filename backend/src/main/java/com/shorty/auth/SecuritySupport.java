package com.shorty.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecuritySupport {

    private SecuritySupport() {}

    public static AuthPrincipal requireUser() {
        return current().orElseThrow(() -> new com.shorty.api.ApiException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required"));
    }

    public static java.util.Optional<AuthPrincipal> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return java.util.Optional.of(principal);
        }
        return java.util.Optional.empty();
    }

    public static Long currentUserIdOrNull() {
        return current().map(AuthPrincipal::userId).orElse(null);
    }
}
