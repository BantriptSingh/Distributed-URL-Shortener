package com.shorty.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UnlockTokenService {

    private final byte[] secret;
    private final Duration ttl;

    public UnlockTokenService(
            @Value("${UNLOCK_TOKEN_SECRET:change-me-unlock-min-32-chars}") String secret,
            @Value("${app.unlock-ttl:5m}") Duration ttl) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
    }

    public String issue(String shortCode) {
        long exp = Instant.now().plus(ttl).getEpochSecond();
        String payload = shortCode.toLowerCase() + "." + exp;
        return payload + "." + sign(payload);
    }

    public boolean valid(String shortCode, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        int lastDot = token.lastIndexOf('.');
        if (lastDot <= 0) {
            return false;
        }
        String payload = token.substring(0, lastDot);
        String sig = token.substring(lastDot + 1);
        if (!sign(payload).equals(sig)) {
            return false;
        }
        String[] parts = payload.split("\\.", 2);
        if (parts.length != 2) {
            return false;
        }
        if (!parts[0].equalsIgnoreCase(shortCode)) {
            return false;
        }
        try {
            long exp = Long.parseLong(parts[1]);
            return Instant.now().getEpochSecond() <= exp;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
