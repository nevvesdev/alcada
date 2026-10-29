package br.com.nevvesdev.alcada.infrastructure.metrics;

import br.com.nevvesdev.alcada.domain.model.CreditLineApproved;
import br.com.nevvesdev.alcada.domain.model.CreditLineRejected;
import br.com.nevvesdev.alcada.domain.model.DomainEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CreditRequestMetrics {

    private static final String OUTCOME_METRIC = "alcada.credit.request.outcome";

    private final MeterRegistry registry;

    public CreditRequestMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordOutcome(DomainEvent event) {
        switch (event) {
            case CreditLineApproved approved -> increment("approved", "none");
            case CreditLineRejected rejected -> increment("rejected", rejected.reason().name());
        }
    }

    private void increment(String outcome, String reason) {
        Counter.builder(OUTCOME_METRIC)
                .description("Solicitações de crédito concluídas por resultado e motivo")
                .tag("outcome", outcome)
                .tag("reason", reason)
                .register(registry)
                .increment();
    }
}