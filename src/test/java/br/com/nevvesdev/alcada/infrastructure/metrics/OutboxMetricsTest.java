package br.com.nevvesdev.alcada.infrastructure.metrics;

import br.com.nevvesdev.alcada.TestcontainersConfiguration;
import br.com.nevvesdev.alcada.infrastructure.persistence.OutboxEventJpaEntity;
import br.com.nevvesdev.alcada.infrastructure.persistence.SpringDataOutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxMetricsTest {

    private static final Instant T0 = Instant.parse("2026-10-29T12:00:00Z");
    private static final int MAX_ATTEMPTS = 3;

    @Autowired
    private SpringDataOutboxEventRepository outbox;

    @BeforeEach
    void cleanDatabase() {
        outbox.deleteAll();
    }

    private OutboxEventJpaEntity event() {
        return new OutboxEventJpaEntity(
                UUID.randomUUID(), "CreditRequest", UUID.randomUUID(), "CreditLineApproved", "{}", T0);
    }

    @Test
    void shouldExposePendingAndDeadEvents() {
        OutboxEventJpaEntity pending = event();
        OutboxEventJpaEntity retrying = event();
        retrying.markFailed("erro");
        OutboxEventJpaEntity dead = event();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            dead.markFailed("erro");
        }
        OutboxEventJpaEntity published = event();
        published.markPublished(T0.plusSeconds(1));
        outbox.saveAll(java.util.List.of(pending, retrying, dead, published));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new OutboxMetrics(outbox, MAX_ATTEMPTS).bindTo(registry);

        assertThat(registry.get("alcada.outbox.pending").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("alcada.outbox.dead").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void shouldReportZeroWhenOutboxIsEmpty() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new OutboxMetrics(outbox, MAX_ATTEMPTS).bindTo(registry);

        assertThat(registry.get("alcada.outbox.pending").gauge().value()).isZero();
        assertThat(registry.get("alcada.outbox.dead").gauge().value()).isZero();
    }
}