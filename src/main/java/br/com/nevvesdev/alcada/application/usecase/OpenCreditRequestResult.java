package br.com.nevvesdev.alcada.application.usecase;

import br.com.nevvesdev.alcada.domain.model.CreditRequest;

public record OpenCreditRequestResult(CreditRequest creditRequest, boolean newlyCreated) {
}