package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void shouldNormalizeScaleToTwoDecimals() {
        assertThat(Money.of("10.5").amount()).hasToString("10.50");
    }

    @Test
    void shouldRoundHalfEven() {
        assertThat(Money.of("2.345").amount()).hasToString("2.34");
        assertThat(Money.of("2.355").amount()).hasToString("2.36");
    }

    @Test
    void shouldTreatSameValueWithDifferentScaleAsEqual() {
        assertThat(Money.of("10")).isEqualTo(Money.of("10.00"));
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> Money.of("-0.01")).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> new Money(null)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldRejectInvalidText() {
        assertThatThrownBy(() -> Money.of("abc")).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldAddAmounts() {
        assertThat(Money.of("10.10").plus(Money.of("0.20"))).isEqualTo(Money.of("10.30"));
    }

    @Test
    void shouldSubtractAmounts() {
        assertThat(Money.of("10.00").minus(Money.of("5.00"))).isEqualTo(Money.of("5.00"));
    }

    @Test
    void shouldRejectSubtractionBelowZero() {
        assertThatThrownBy(() -> Money.of("1.00").minus(Money.of("1.01")))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldMultiplyByFactor() {
        assertThat(Money.of("1000").multiply(new BigDecimal("5"))).isEqualTo(Money.of("5000"));
    }

    @Test
    void shouldCompareAmounts() {
        assertThat(Money.of("10.01").isGreaterThan(Money.of("10.00"))).isTrue();
        assertThat(Money.of("10.00").isGreaterThan(Money.of("10.00"))).isFalse();
    }

    @Test
    void shouldIdentifyZero() {
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.of("0.01").isZero()).isFalse();
    }
}