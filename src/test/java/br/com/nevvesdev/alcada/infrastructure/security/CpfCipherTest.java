package br.com.nevvesdev.alcada.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfCipherTest {

    private final CpfCipher cipher = new CpfCipher("segredo-de-teste");

    @Test
    void shouldRoundTripTheCpf() {
        String encrypted = cipher.encrypt("52998224725");

        assertThat(cipher.decrypt(encrypted)).isEqualTo("52998224725");
    }

    @Test
    void shouldStoreVersionedCiphertextWithoutTheClearText() {
        String encrypted = cipher.encrypt("52998224725");

        assertThat(encrypted).startsWith("enc:v1:").doesNotContain("52998224725");
    }

    @Test
    void shouldProduceDifferentCiphertextForTheSameInput() {
        assertThat(cipher.encrypt("52998224725")).isNotEqualTo(cipher.encrypt("52998224725"));
    }

    @Test
    void shouldReadLegacyClearTextValues() {
        assertThat(cipher.decrypt("52998224725")).isEqualTo("52998224725");
    }

    @Test
    void shouldDetectTamperedCiphertext() {
        String encrypted = cipher.encrypt("52998224725");
        char last = encrypted.charAt(encrypted.length() - 2);
        char replacement = last == 'A' ? 'B' : 'A';
        String tampered = encrypted.substring(0, encrypted.length() - 2) + replacement
                + encrypted.charAt(encrypted.length() - 1);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldNotDecryptWithAnotherSecret() {
        String encrypted = cipher.encrypt("52998224725");

        assertThatThrownBy(() -> new CpfCipher("outro-segredo").decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectTruncatedCiphertext() {
        assertThatThrownBy(() -> cipher.decrypt("enc:v1:QUJD")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectInvalidBase64() {
        assertThatThrownBy(() -> cipher.decrypt("enc:v1:@@@@")).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectBlankSecret(String secret) {
        assertThatThrownBy(() -> new CpfCipher(secret)).isInstanceOf(IllegalArgumentException.class);
    }
}