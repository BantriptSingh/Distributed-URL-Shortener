package com.shorty.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnlockTokenServiceTest {

    private final UnlockTokenService tokens =
            new UnlockTokenService("test-unlock-secret-32-chars-minx", java.time.Duration.ofMinutes(5));

    @Test
    void tokenIsBoundToShortCode() {
        String token = tokens.issue("abcAAAA");
        assertThat(tokens.valid("abcaaaa", token)).isTrue();
        assertThat(tokens.valid("zzzzzzz", token)).isFalse();
    }
}
