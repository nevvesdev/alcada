package br.com.nevvesdev.alcada.infrastructure.metrics;

import br.com.nevvesdev.alcada.infrastructure.outbox.OutboxMessage;
import br.com.nevvesdev.alcada.infrastructure.outbox.OutboxMessagePublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeteredOutboxMessagePublisherTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private static OutboxMessage message() {
        return new OutboxMessage(
                UUID.randomUUID(), "CreditRequest", UUID.randomUUID(), "CreditLineApproved", "{}",
                Instant.parse("2026-10-29T12:00:00Z"));
    }

    @Test
    void shouldCountSuccessfulPublications() {
        MeteredOutboxMessagePublisher publisher = new MeteredOutboxMessagePublisher(published -> { }, registry);

        publisher.publish(message());
        publisher.publish(message());

        assertThat(registry.get("alcada.outbox.published")
                .tag("event_type", "CreditLineApproved").counter().count()).isEqualTo(2.0);
        assertThat(registry.find("alcada.outbox.publish.failures").counter()).isNull();
    }

    @Test
    void shouldCountFailuresAndRethrow() {
        OutboxMessagePublisher failing = published -> {
            throw new IllegalStateException("broker fora");
        };
        MeteredOutboxMessagePublisher publisher = new MeteredOutboxMessagePublisher(failing, registry);

        assertThatThrownBy(() -> publisher.publish(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("broker fora");

        assertThat(registry.get("alcada.outbox.publish.failures")
                .tag("event_type", "CreditLineApproved").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("alcada.outbox.published").counter()).isNull();
    }
}