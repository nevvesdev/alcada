package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditScoreTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 500, 999, 1000})
    void shouldAcceptScoreInsideRange(int value) {
        assertThatCode(() -> CreditScore.of(value)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1001, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void shouldRejectScoreOutsideRange(int value) {
        assertThatThrownBy(() -> CreditScore.of(value)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldCompareScores() {
        assertThat(CreditScore.of(399).isLowerThan(CreditScore.of(400))).isTrue();
        assertThat(CreditScore.of(400).isLowerThan(CreditScore.of(400))).isFalse();
        assertThat(CreditScore.of(500)).isGreaterThan(CreditScore.of(400));
    }
}