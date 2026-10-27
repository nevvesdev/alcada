package br.com.nevvesdev.alcada.application.usecase;

import br.com.nevvesdev.alcada.application.exception.CreditRequestNotFoundException;
import br.com.nevvesdev.alcada.application.port.out.CreditRequestRepository;
import br.com.nevvesdev.alcada.application.port.out.StalledCreditRequestPort;
import br.com.nevvesdev.alcada.application.saga.CreditSagaOrchestrator;
import br.com.nevvesdev.alcada.domain.model.CreditRequest;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RecoverStalledRequestsUseCase {

    private final StalledCreditRequestPort stalledRequests;
    private final CreditRequestRepository repository;
    private final CreditSagaOrchestrator orchestrator;
    private final Clock clock;
    private final Duration staleAfter;
    private final Duration lease;
    private final int batchSize;

    public RecoverStalledRequestsUseCase(StalledCreditRequestPort stalledRequests,
                                         CreditRequestRepository repository,
                                         CreditSagaOrchestrator orchestrator,
                                         Clock clock,
                                         Duration staleAfter,
                                         Duration lease,
                                         int batchSize) {
        this.stalledRequests = Objects.requireNonNull(stalledRequests, "stalledRequests");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.staleAfter = requirePositive(staleAfter, "staleAfter");
        this.lease = requirePositive(lease, "lease");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize deve ser maior ou igual a 1");
        }
        this.batchSize = batchSize;
    }

    public RecoverySummary execute() {
        Instant now = clock.instant();
        List<CreditRequestId> claimed = stalledRequests.claim(now.minus(staleAfter), now, lease, batchSize);
        List<CreditRequestId> failed = new ArrayList<>();
        for (CreditRequestId id : claimed) {
            try {
                CreditRequest request = repository.findById(id)
                        .orElseThrow(() -> new CreditRequestNotFoundException(id));
                orchestrator.run(request);
            } catch (RuntimeException exception) {
                failed.add(id);
            }
        }
        return new RecoverySummary(claimed.size(), failed);
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " deve ser uma duração positiva");
        }
        return value;
    }
}