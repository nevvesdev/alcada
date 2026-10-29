package br.com.nevvesdev.alcada.infrastructure.metrics;

import br.com.nevvesdev.alcada.infrastructure.outbox.OutboxMessage;
import br.com.nevvesdev.alcada.infrastructure.outbox.OutboxMessagePublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class MeteredOutboxMessagePublisher implements OutboxMessagePublisher {

    private final OutboxMessagePublisher delegate;
    private final MeterRegistry registry;

    public MeteredOutboxMessagePublisher(OutboxMessagePublisher delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public void publish(OutboxMessage message) {
        try {
            delegate.publish(message);
        } catch (RuntimeException exception) {
            Counter.builder("alcada.outbox.publish.failures")
                    .description("Falhas ao publicar eventos do outbox")
                    .tag("event_type", message.eventType())
                    .register(registry)
                    .increment();
            throw exception;
        }
        Counter.builder("alcada.outbox.published")
                .description("Eventos do outbox publicados com sucesso")
                .tag("event_type", message.eventType())
                .register(registry)
                .increment();
    }
}