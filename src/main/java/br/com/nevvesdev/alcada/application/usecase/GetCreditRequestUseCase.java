package br.com.nevvesdev.alcada.application.usecase;

import br.com.nevvesdev.alcada.application.exception.CreditRequestNotFoundException;
import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;

import java.util.Objects;

public final class GetCreditRequestUseCase {

    private final CreditRequestRepository repository;

    public GetCreditRequestUseCase(CreditRequestRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public CreditRequest execute(CreditRequestId id) {
        return repository.findById(id).orElseThrow(() -> new CreditRequestNotFoundException(id));
    }
}