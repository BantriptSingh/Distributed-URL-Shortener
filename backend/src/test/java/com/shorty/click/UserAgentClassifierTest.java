package com.shorty.click;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserAgentClassifierTest {

    private final UserAgentClassifier classifier = new UserAgentClassifier();

    @Test
    void flagsCurlAsBot() {
        var c = classifier.classify("curl/8.5.0");
        assertThat(c.bot()).isTrue();
        assertThat(c.browser()).isEqualTo("other");
    }

    @Test
    void parsesChromeDesktop() {
        var c = classifier.classify(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
        assertThat(c.bot()).isFalse();
        assertThat(c.browser()).isEqualTo("chrome");
        assertThat(c.os()).isEqualTo("windows");
        assertThat(c.deviceType()).isEqualTo("desktop");
    }
}
