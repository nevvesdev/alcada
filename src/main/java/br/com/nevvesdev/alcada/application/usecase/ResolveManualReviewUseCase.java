package br.com.nevvesdev.alcada.application.usecase;

import br.com.nevvesdev.alcada.application.exception.CreditRequestNotFoundException;
import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.application.saga.CreditSagaOrchestrator;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;

import java.util.Objects;

public final class ResolveManualReviewUseCase {

    private final CreditRequestRepository repository;
    private final CreditSagaOrchestrator orchestrator;

    public ResolveManualReviewUseCase(CreditRequestRepository repository, CreditSagaOrchestrator orchestrator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
    }

    public CreditRequest approve(CreditRequestId id) {
        return orchestrator.approveReview(load(id));
    }

    public CreditRequest deny(CreditRequestId id) {
        return orchestrator.denyReview(load(id));
    }

    private CreditRequest load(CreditRequestId id) {
        return repository.findById(id).orElseThrow(() -> new CreditRequestNotFoundException(id));
    }
}