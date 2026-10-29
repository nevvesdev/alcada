package br.com.nevvesdev.alcada.infrastructure.metrics;

import br.com.nevvesdev.alcada.domain.model.CreditLineApproved;
import br.com.nevvesdev.alcada.domain.model.CreditLineRejected;
import br.com.nevvesdev.alcada.domain.model.CreditRequestId;
import br.com.nevvesdev.alcada.domain.model.Money;
import br.com.nevvesdev.alcada.domain.model.RejectionReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CreditRequestMetricsTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CreditRequestMetrics metrics = new CreditRequestMetrics(registry);

    @Test
    void shouldCountApprovals() {
        metrics.recordOutcome(new CreditLineApproved(CreditRequestId.generate(), Money.of("100"), NOW));
        metrics.recordOutcome(new CreditLineApproved(CreditRequestId.generate(), Money.of("200"), NOW));

        double count = registry.get("alcada.credit.request.outcome")
                .tag("outcome", "approved")
                .tag("reason", "none")
                .counter()
                .count();

        assertThat(count).isEqualTo(2.0);
    }

    @Test
    void shouldCountRejectionsByReason() {
        metrics.recordOutcome(new CreditLineRejected(CreditRequestId.generate(), RejectionReason.LOW_SCORE, NOW));
        metrics.recordOutcome(new CreditLineRejected(CreditRequestId.generate(), RejectionReason.LOW_SCORE, NOW));
        metrics.recordOutcome(
                new CreditLineRejected(CreditRequestId.generate(), RejectionReason.BUREAU_UNAVAILABLE, NOW));

        assertThat(registry.get("alcada.credit.request.outcome")
                .tag("outcome", "rejected").tag("reason", "LOW_SCORE").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("alcada.credit.request.outcome")
                .tag("outcome", "rejected").tag("reason", "BUREAU_UNAVAILABLE").counter().count()).isEqualTo(1.0);
    }
}