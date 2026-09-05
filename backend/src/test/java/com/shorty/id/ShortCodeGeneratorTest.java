package com.shorty.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void generatesBase62OfRequestedLength() {
        String code = generator.next(7);
        assertThat(code).hasSize(7);
        assertThat(code.chars().allMatch(c -> ShortCodeGenerator.ALPHABET.indexOf(c) >= 0)).isTrue();
    }

    @Test
    void rejectsLengthBelowSeven() {
        assertThatThrownBy(() -> generator.next(6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manyDrawsAreUniqueWithHighProbability() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.next(7));
        }
        assertThat(seen).hasSize(10_000);
    }

    @Test
    void isNotAFunctionOfSnowflakeIds() {
        SnowflakeIdGenerator snowflake = new SnowflakeIdGenerator(1);
        String a = generator.next(7);
        long id = snowflake.nextId();
        String b = generator.next(7);
        assertThat(a).isNotEqualTo(Long.toString(id, 36));
        assertThat(b).isNotEqualTo(Long.toString(id, 36));
        assertThat(a).isNotEqualTo(b);
    }
}
