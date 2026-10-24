package br.com.nevvesdev.alcada.domain.model;

import br.com.nevvesdev.alcada.domain.exception.DomainValidationException;

import java.util.UUID;

public record CreditRequestId(UUID value) {

    public CreditRequestId {
        if (value == null) {
            throw new DomainValidationException("Identificador da solicitação é obrigatório");
        }
    }

    public static CreditRequestId generate() {
        return new CreditRequestId(UUID.randomUUID());
    }

    public static CreditRequestId of(String value) {
        if (value == null) {
            throw new DomainValidationException("Identificador da solicitação é obrigatório");
        }
        try {
            return new CreditRequestId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw new DomainValidationException("Identificador da solicitação inválido");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}