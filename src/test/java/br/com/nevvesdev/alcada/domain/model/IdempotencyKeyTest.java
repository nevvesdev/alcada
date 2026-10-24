package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {

    @Test
    void shouldTrimSurroundingSpaces() {
        assertThat(IdempotencyKey.of("  key-123  ").value()).isEqualTo("key-123");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void shouldRejectBlankKey(String raw) {
        assertThatThrownBy(() -> IdempotencyKey.of(raw)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldAcceptKeyWithMaximumLength() {
        assertThatCode(() -> IdempotencyKey.of("a".repeat(128))).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectKeyAboveMaximumLength() {
        assertThatThrownBy(() -> IdempotencyKey.of("a".repeat(129)))
                .isInstanceOf(DomainValidationException.class);
    }
}