package br.com.nevvesdev.alcada.application.exception;

import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;

public class IdempotencyKeyConflictException extends ApplicationException {

    public IdempotencyKeyConflictException(IdempotencyKey key) {
        super("Já existe uma solicitação com a chave de idempotência " + key.value());
    }
}