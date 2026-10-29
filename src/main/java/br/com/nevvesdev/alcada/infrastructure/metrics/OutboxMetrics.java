package br.com.nevvesdev.alcada.infrastructure.metrics;

import br.com.nevvesdev.alcada.infrastructure.persistence.SpringDataOutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

public class OutboxMetrics implements MeterBinder {

    private final SpringDataOutboxEventRepository repository;
    private final int maxAttempts;

    public OutboxMetrics(SpringDataOutboxEventRepository repository, int maxAttempts) {
        this.repository = repository;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("alcada.outbox.pending", repository,
                        source -> source.countByPublishedAtIsNullAndAttemptsLessThan(maxAttempts))
                .description("Eventos do outbox aguardando publicação")
                .register(registry);
        Gauge.builder("alcada.outbox.dead", repository,
                        source -> source.countByPublishedAtIsNullAndAttemptsGreaterThanEqual(maxAttempts))
                .description("Eventos do outbox que esgotaram as tentativas")
                .register(registry);
    }
}