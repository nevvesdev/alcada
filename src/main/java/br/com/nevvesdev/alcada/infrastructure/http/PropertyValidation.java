package br.com.nevvesdev.alcada.infrastructure.http;

import java.time.Duration;

final class PropertyValidation {

    private PropertyValidation() {
    }

    static void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Propriedade " + property + " deve ser uma duração positiva");
        }
    }
}