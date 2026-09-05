package com.shorty.id;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Public short codes: independent CSPRNG Base62 strings. Not derived from Snowflake IDs.
 */
@Component
public class ShortCodeGenerator {

    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String next(int length) {
        if (length < 7) {
            throw new IllegalArgumentException("short codes must be at least 7 characters");
        }
        char[] buf = new char[length];
        for (int i = 0; i < length; i++) {
            buf[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        }
        return new String(buf);
    }
}
