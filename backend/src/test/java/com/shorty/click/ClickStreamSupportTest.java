package com.shorty.click;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClickStreamSupportTest {

    @Test
    void busyGroupIsDetectedOnNestedCauses() {
        RuntimeException nested = new RuntimeException(
                "BUSYGROUP Consumer Group name already exists", new IllegalStateException("inner"));
        assertThat(ClickStreamSupport.isBusyGroup(new RuntimeException("wrap", nested))).isTrue();
        assertThat(ClickStreamSupport.isBusyGroup(new RuntimeException("NOGROUP"))).isFalse();
    }
}
