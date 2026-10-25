package br.com.nevvesdev.alcada.application.port.out;

import br.com.nevvesdev.alcada.domain.model.Cpf;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.Money;

public interface LimitReservationPort {

    void reserve(CreditRequestId requestId, Cpf cpf, Money amount);

    void confirm(CreditRequestId requestId);

    void release(CreditRequestId requestId);
}