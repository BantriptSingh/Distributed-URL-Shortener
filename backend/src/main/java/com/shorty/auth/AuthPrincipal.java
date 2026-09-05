package com.shorty.auth;

import java.util.List;
import java.util.Set;

public record AuthPrincipal(long userId, String email, Set<String> scopes, boolean apiKey) {

    public static AuthPrincipal jwt(long userId, String email) {
        return new AuthPrincipal(userId, email, Set.of("read", "write"), false);
    }

    public boolean canRead() {
        return scopes.contains("read") || scopes.contains("write");
    }

    public boolean canWrite() {
        return scopes.contains("write");
    }

    public List<String> scopeList() {
        return List.copyOf(scopes);
    }
}
