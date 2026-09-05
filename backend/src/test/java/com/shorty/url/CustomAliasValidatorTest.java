package com.shorty.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shorty.api.ApiException;
import org.junit.jupiter.api.Test;

class CustomAliasValidatorTest {

    private final CustomAliasValidator validator = new CustomAliasValidator();

    @Test
    void lowercasesAndAcceptsValidAlias() {
        assertThat(validator.validateAndNormalize("My-Link")).isEqualTo("my-link");
    }

    @Test
    void rejectsReservedWordsRegardlessOfCase() {
        assertThatThrownBy(() -> validator.validateAndNormalize("API"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "reserved_alias");
    }

    @Test
    void rejectsIllegalCharset() {
        assertThatThrownBy(() -> validator.validateAndNormalize("ok_nope"))
                .isInstanceOf(ApiException.class);
    }
}
