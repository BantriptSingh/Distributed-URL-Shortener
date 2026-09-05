package com.shorty.auth;

import com.shorty.api.ApiException;
import com.shorty.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {

    private final ApiKeyRepository keys;
    private final SnowflakeIdGenerator ids;

    public ApiKeyService(ApiKeyRepository keys, SnowflakeIdGenerator ids) {
        this.keys = keys;
        this.ids = ids;
    }

    @Transactional
    public CreatedKey create(long userId, List<String> scopes) {
        List<String> resolved = normalizeScopes(scopes);
        String raw = "sk_live_" + TokenHasher.randomUrlToken(16);
        ApiKeyEntity row = new ApiKeyEntity();
        row.setId(ids.nextId());
        row.setUserId(userId);
        row.setKeyHash(TokenHasher.sha256Hex(raw));
        row.setPrefix(raw.substring(0, Math.min(12, raw.length())));
        row.setScopes(resolved);
        row.setCreatedAt(Instant.now());
        keys.save(row);
        return new CreatedKey(row.getId(), raw, row.getPrefix(), resolved, row.getCreatedAt());
    }

    public List<MaskedKey> list(long userId) {
        return keys.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(k -> new MaskedKey(
                        k.getId(),
                        k.getPrefix() + "…",
                        k.getScopes(),
                        k.getCreatedAt(),
                        k.getLastUsedAt(),
                        k.getRevokedAt() != null))
                .toList();
    }

    @Transactional
    public void revoke(long userId, long keyId) {
        ApiKeyEntity row = keys.findById(keyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "API key not found"));
        if (row.getUserId() != userId) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "API key not found");
        }
        row.setRevokedAt(Instant.now());
        keys.save(row);
    }

    @Transactional
    public AuthPrincipal authenticateRaw(String raw) {
        ApiKeyEntity row = keys.findByKeyHash(TokenHasher.sha256Hex(raw)).orElse(null);
        if (row == null || row.getRevokedAt() != null) {
            return null;
        }
        row.setLastUsedAt(Instant.now());
        keys.save(row);
        return new AuthPrincipal(row.getUserId(), "", Set.copyOf(row.getScopes()), true);
    }

    private static List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of("read", "write");
        }
        return scopes.stream()
                .map(String::toLowerCase)
                .filter(s -> s.equals("read") || s.equals("write"))
                .distinct()
                .toList();
    }

    public record CreatedKey(long id, String key, String prefix, List<String> scopes, Instant createdAt) {}

    public record MaskedKey(
            long id, String prefix, List<String> scopes, Instant createdAt, Instant lastUsedAt, boolean revoked) {}

    public record CreateKeyRequest(List<String> scopes) {}
}
