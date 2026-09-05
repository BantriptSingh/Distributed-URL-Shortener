package com.shorty.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShortCodeNotDerivedFromIdTest {

    @Test
    void snowflakeIdsIncreaseWhilePublicCodesDoNotEncodeThem() {
        SnowflakeIdGenerator ids = new SnowflakeIdGenerator(7);
        ShortCodeGenerator codes = new ShortCodeGenerator();
        List<Long> generatedIds = new ArrayList<>();
        List<String> generatedCodes = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            generatedIds.add(ids.nextId());
            generatedCodes.add(codes.next(7));
        }
        for (int i = 1; i < generatedIds.size(); i++) {
            assertThat(generatedIds.get(i)).isGreaterThan(generatedIds.get(i - 1));
        }
        for (int i = 0; i < generatedCodes.size(); i++) {
            String code = generatedCodes.get(i);
            assertThat(code).hasSize(7).matches("[0-9A-Za-z]{7}");
            assertThat(code).isNotEqualTo(Long.toString(generatedIds.get(i), 36));
            assertThat(code).isNotEqualTo(Long.toUnsignedString(generatedIds.get(i), 36));
        }
        long strictlyIncreasingCodes = 0;
        for (int i = 1; i < generatedCodes.size(); i++) {
            if (generatedCodes.get(i).compareTo(generatedCodes.get(i - 1)) > 0) {
                strictlyIncreasingCodes++;
            }
        }
        assertThat(strictlyIncreasingCodes).isLessThan(generatedCodes.size() - 1);
    }
}
