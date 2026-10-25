package br.com.nevvesdev.alcada.application.exception;

import br.com.nevvesdev.alcada.domain.model.CreditRequestId;

public class CreditRequestNotFoundException extends ApplicationException {

    public CreditRequestNotFoundException(CreditRequestId id) {
        super("Solicitação de crédito não encontrada: " + id);
    }
}