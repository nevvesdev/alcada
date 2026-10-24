package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "111.444.777-35"})
    void shouldAcceptValidCpf(String raw) {
        assertThatCode(() -> Cpf.of(raw)).doesNotThrowAnyException();
    }

    @Test
    void shouldStoreOnlyDigits() {
        assertThat(Cpf.of("529.982.247-25").value()).isEqualTo("52998224725");
    }

    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-24", "111.111.111-11", "000.000.000-00", "123", "abc", ""})
    void shouldRejectInvalidCpf(String raw) {
        assertThatThrownBy(() -> Cpf.of(raw)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldRejectNullCpf() {
        assertThatThrownBy(() -> Cpf.of(null)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void shouldMaskCpf() {
        assertThat(Cpf.of("52998224725").masked()).isEqualTo("***.982.247-**");
    }

    @Test
    void shouldNotExposeFullCpfInToString() {
        assertThat(Cpf.of("52998224725")).hasToString("***.982.247-**");
    }

    @Test
    void shouldTreatSameDigitsAsEqual() {
        assertThat(Cpf.of("529.982.247-25")).isEqualTo(Cpf.of("52998224725"));
    }
}