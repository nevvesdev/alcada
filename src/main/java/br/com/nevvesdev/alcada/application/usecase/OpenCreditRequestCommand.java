package br.com.nevvesdev.alcada.application.usecase;

import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.IdempotencyKey;
import br.com.nevvesdev.alcada.domain.model.Money;

import java.util.Objects;

public record OpenCreditRequestCommand(IdempotencyKey idempotencyKey,
                                       Cpf cpf,
                                       Money requestedAmount,
                                       Money declaredIncome) {

    public OpenCreditRequestCommand {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(cpf, "cpf");
        Objects.requireNonNull(requestedAmount, "requestedAmount");
        Objects.requireNonNull(declaredIncome, "declaredIncome");
    }
}