package com.shorty.url;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlReputationTest {

    @Test
    void classpathDenylistIncludesBlockedTestHost() {
        UrlReputation reputation = new UrlReputation();
        assertThat(reputation.isBlocked("evil.blocked.test")).isTrue();
        assertThat(reputation.isBlocked("sub.evil.blocked.test")).isTrue();
        assertThat(reputation.isBlocked("example.com")).isFalse();
    }
}
