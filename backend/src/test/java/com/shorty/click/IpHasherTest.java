package com.shorty.click;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IpHasherTest {

    @Test
    void hashIsSha256HexAndNeverRawIp() {
        IpHasher hasher = new IpHasher("test-salt");
        String ip = "203.0.113.9";
        String hash = hasher.hash(ip);
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hash).isNotEqualTo(ip);
        assertThat(hash).doesNotContain(ip);
        assertThat(hasher.hash(ip)).isEqualTo(hash);
        assertThat(hasher.hash("198.51.100.1")).isNotEqualTo(hash);
    }
}
