package com.shorty.click;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IpHasher {

    private final String salt;

    public IpHasher(@Value("${IP_HASH_SALT:change-me-ip-salt-min-16}") String salt) {
        this.salt = salt;
    }

    public String hash(String ip) {
        String value = salt + "|" + (ip == null || ip.isBlank() ? "unknown" : ip.trim());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
