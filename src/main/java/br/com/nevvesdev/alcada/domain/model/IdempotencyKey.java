package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;

public record IdempotencyKey(String value) {

    private static final int MAX_LENGTH = 128;

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("Chave de idempotência é obrigatória");
        }
        value = value.trim();
        if (value.length() > MAX_LENGTH) {
            throw new DomainValidationException(
                    "Chave de idempotência deve ter no máximo " + MAX_LENGTH + " caracteres");
        }
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }
}